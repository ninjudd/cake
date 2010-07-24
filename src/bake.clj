(ns bake)

; must be declared first so other namespaces can access them
(def current-task nil)
(defonce bake-project (atom nil))
(def project nil)

(ns bake
  (:use useful bake.project)
  (:require [bake.ant :as ant]
            [bake.server :as server])
  (:import [java.io File FileReader FileInputStream InputStreamReader OutputStreamWriter BufferedReader]
           [java.net Socket ConnectException]
           [java.util Properties]))

(defn verbose? [opts]
  (or (:v opts) (:verbose opts)))

(defmacro defproject [name version & args]
  (let [opts (apply hash-map args)
        [tasks task-opts] (split-with symbol? (:tasks opts))
        task-opts (apply hash-map task-opts)]    
    `(do (compare-and-set! bake-project nil (create-project '~name ~version '~opts))
         ; @bake-project must be set before we include the tasks for cake to work.
         (require '~'[bake.tasks help jar test compile dependencies swank clean])
         ~@(for [ns tasks]
             `(try (require '~ns)
                   (catch java.io.FileNotFoundException e#
                     (println "warning: could not load" '~ns))))
         (undeftask ~@(:exclude task-opts)))))

(defn expand-path [& path]
  (cond (instance? File (first path))
        (cons (.getName (first path)) (rest path))
        
        (when-let [fp (first path)] (.startsWith fp "~"))
        (apply list (System/getProperty "user.home")
               (.substring (first path) 1)
               (rest path))

        :else (cons (:root project) path)))

(defn file-name [& path]
  (apply str (interpose (File/separator) (apply expand-path path))))

(defn file
  "Create a File object from a string or seq"
  [& path]
  (File. (apply file-name path)))

(defn update-task [task deps doc actions]
  {:pre [(every? symbol? deps) (every? fn? actions)]}
  (let [task (or task {:actions [] :deps #{} :doc []})]
    (-> task
        (update :deps    into deps)
        (update :doc     into doc)
        (update :actions into actions))))

(defonce tasks (atom {}))
(def run? nil)

(def implicit-tasks
  {'repl    "Start an interactive shell with history and tab completion."
   'stop    "Stop bake jvm processes."
   'start   "Start bake jvm processes."
   'restart "Restart bake jvm processes."
   'reload  "Reload any .clj files that have changed or restart."
   'ps      "List running bake jvm processes for all projects."
   'kill    "Kill running bake jvm processes. Use -9 to force or --all for all projects."})

(defmacro deftask
  "Define a bake task. Each part of the body is optional. Task definitions can
   be broken up among multiple deftask calls and even multiple files:
   (deftask foo #{bar baz} ; a set of prerequisites for this task
     \"Documentation for task.\"
     (do-something)
     (do-something-else))"
  [name & body]
  (verify (not (implicit-tasks name)) (format "Cannot redefine %s task" name))
  (let [[deps body] (if (set? (first body))
                      [(first body) (rest body)]
                      [#{} body])
        [doc actions] (split-with string? body)
        actions (vec (map #(list 'fn [] %) actions))]
    `(swap! tasks update '~name update-task '~deps '~doc ~actions)))

(defmacro undeftask [& names]
  `(swap! tasks dissoc ~@(map #(list 'quote %) names)))

(defn run-task
  "Execute the specified task after executing all prerequisite tasks."
  [form]
  (let [name form, task (@tasks name)]
    (if (nil? task)
      (println "unknown task:" name)
      (when-not (run? name)
        (doseq [dep (:deps task)] (run-task dep))
        (binding [current-task name]
          (doseq [action (:actions task)] (action)))
        (set! run? (assoc run? name true))))))

(defmacro invoke [name]
  `(run-task '~name))

(def cake-port nil)

(defn- cake-connect [port]
  (loop []
    (if-let [socket (try (Socket. "localhost" (int port)) (catch ConnectException e))]
      socket
      (recur))))

(defn- quote-if
  "We need to quote the binding keys so they are not evaluated within the cake syntax-quote and the
   binding values so they are not evaluated in the cake* syntax-quote. This function makes that possible."
  [pred bindings]
  (reduce
   (fn [v form]
     (if (pred (count v))
       (conj v (list 'quote form))
       (conj v form)))
   [] bindings))

(defn bake* [ns-forms bindings body]
  (if (nil? cake-port)
    (println "bake command not supported. perhaps you don't have a project.clj")
    (let [ns     (symbol (str "cake.task." (name current-task)))
          forms `[(~'ns ~ns (:use ~'[cake :only [project]]) ~@ns-forms)
                  (~'let ~(quote-if odd? bindings) ~@body)]
          socket (cake-connect (int cake-port))
          reader (BufferedReader. (InputStreamReader. (.getInputStream socket)))
          writer (OutputStreamWriter. (.getOutputStream socket))]
      (doto writer
        (.write (prn-str forms))
        (.flush))
      (while-let [line (.readLine reader)]
        (println line))
      (flush)
      (.close socket))))

(defmacro bake
  "Execute code in a separate jvm with the classpath of your projects. Bindings allow passing
   state to the project jvm. Namespace forms like use and require must be specified before bindings."
  {:arglists '([ns-forms* bindings body*])}
  [& forms]
  (let [[ns-forms [bindings & body]] (split-with (complement vector?) forms)
        bindings (into ['opts 'bake/opts] bindings)]
    `(bake* '~ns-forms ~(quote-if even? bindings) '~body)))

(def opts   nil)
(def config nil)

(defn read-config []
  (let [file (File. ".bake/config")]
    (when (.exists file)
      (with-open [f (FileInputStream. file)]
        (into {} (doto (Properties.) (.load f)))))))

(def readline-marker nil)

(defn process-command [form]
  (let [[task args port] form]
    (binding [project         @bake-project
              ant/ant-project (ant/init-project @bake-project server/*outs*)
              opts            (parse-opts (keyword task) args)
              config          (read-config)
              cake-port       port
              run?            {}
              readline-marker (read)]
      (doseq [dir ["lib" "classes" "build"]]
        (.mkdirs (file dir)))
      (run-task (symbol (or task 'default))))))

(defn task-file? [file]
  (some (partial re-matches #".*\(deftask .*|.*\(defproject .*")
        (line-seq (BufferedReader. (FileReader. file)))))

(defn skip-task-files [load-file]
  (fn [file]
    (if (task-file? file)
      (println "unable to reload file with deftask or defproject in it:" file)
      (load-file file))))

(defn reload-files []
  (binding [clojure.core/load-file (skip-task-files load-file)]
    (server/reload-files)))

(defn prompt-read [prompt & opts]
  (let [opts (apply hash-map opts)
        echo (if (false? (:echo opts)) "@" "")]
    (println (str echo readline-marker prompt))
    (read-line)))

(defn start-server [port]
  (init "project.clj" "build.clj")
  (when-not @bake/bake-project (require '[bake.tasks help new]))
  (server/create port process-command :reload reload-files)
  nil)
