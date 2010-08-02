(ns cake)

; must be declared first so other namespaces can access them
(defonce cake-project (atom nil))
(def current-task nil)
(def project nil)
(def opts    nil)
(def config  nil)

(defn verbose? []
  (boolean (or (:v opts) (:verbose opts))))

(ns cake
  (:use useful cake.project)
  (:require [cake.ant :as ant]
            [cake.server :as server])
  (:import [java.io File FileReader FileInputStream InputStreamReader OutputStreamWriter BufferedReader]
           [java.net Socket ConnectException]
           [java.util Properties]))

(defmacro defproject [name version & args]
  (let [opts (apply hash-map args)
        [tasks task-opts] (split-with symbol? (:tasks opts))
        task-opts (apply hash-map task-opts)]    
    `(do (compare-and-set! cake-project nil (create-project '~name ~version '~opts))
         ; @cake-project must be set before we include the tasks for bake to work.
         (require '~'[cake.tasks help jar test compile dependencies swank clean])
         ~@(for [ns tasks]
             `(try (require '~ns)
                   (catch java.io.FileNotFoundException e#
                     (println "warning: could not load" '~ns))))
         (undeftask ~@(:exclude task-opts)))))

(defn expand-path [& path]
  (cond (instance? File (first path))
        (cons (.getPath (first path)) (rest path))
        
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
   'eval    "Eval the given forms in the project jvm or in the cake jvm if not inside project."
   'stop    "Stop cake jvm processes."
   'start   "Start cake jvm processes."
   'restart "Restart cake jvm processes."
   'reload  "Reload any .clj files that have changed or restart."
   'ps      "List running cake jvm processes for all projects."
   'kill    "Kill running cake jvm processes. Use -9 to force or --all for all projects."})

(defmacro deftask
  "Define a cake task. Each part of the body is optional. Task definitions can
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

(def bake-port nil)

(defn- bake-connect [port]
  (loop []
    (if-let [socket (try (Socket. "localhost" (int port)) (catch ConnectException e))]
      socket
      (recur))))

(defn- quote-if
  "We need to quote the binding keys so they are not evaluated within the bake syntax-quote and the
   binding values so they are not evaluated in the bake* syntax-quote. This function makes that possible."
  [pred bindings]
  (reduce
   (fn [v form]
     (if (pred (count v))
       (conj v (list 'quote form))
       (conj v form)))
   [] bindings))

(defn bake* [ns-forms bindings body]
  (if (nil? bake-port)
    (println "bake not supported. perhaps you don't have a project.clj")
    (let [ns     (symbol (str "bake.task." (name current-task)))
          forms `[(~'ns ~ns (:use ~'[bake :only [project]]) ~@ns-forms)
                  (~'let ~(quote-if odd? bindings) ~@body)]
          socket (bake-connect (int bake-port))
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
        bindings (into ['opts 'cake/opts] bindings)]
    `(bake* '~ns-forms ~(quote-if even? bindings) '~body)))

(defn read-config []
  (let [file (File. ".cake/config")]
    (when (.exists file)
      (with-open [f (FileInputStream. file)]
        (into {} (doto (Properties.) (.load f)))))))

(def readline-marker nil)

(defn process-command [form]
  (let [[task args port] form]
    (binding [project         @cake-project
              ant/ant-project (ant/init-project @cake-project server/*outs*)
              opts            (parse-opts (keyword task) args)
              config          (read-config)
              bake-port       port
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
  (when-not @cake/cake-project (require '[cake.tasks help new]))
  (server/create port process-command :reload reload-files)
  nil)
