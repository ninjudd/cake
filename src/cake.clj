(ns cake)

; must be declared first so other namespaces can access them
(def *current-task* nil)
(def *project*      nil)
(def *opts*         nil)
(def *pwd*          nil)
(def *env*          nil)

(defn verbose? []
  (boolean (or (:v *opts*) (:verbose *opts*))))

(ns cake
  (:use useful)
  (:require cake.project
            [cake.ant :as ant]
            [cake.server :as server])
  (:import [java.io File FileReader InputStreamReader OutputStreamWriter BufferedReader]
           [java.net Socket ConnectException]))

(def *config* cake.project/*config*)

(defmacro defproject [name version & args]
  (let [opts (apply hash-map args)
        [tasks task-opts] (split-with symbol? (:tasks opts))
        task-opts (apply hash-map task-opts)]    
    `(do (alter-var-root #'*project* (fn [_#] (cake.project/create '~name ~version '~opts)))
         (require '~'[cake.tasks help run jar test compile dependencies swank clean])
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

        :else (cons (:root *project*) path)))

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
  {'repl     "Start an interactive shell with history and tab completion."
   'eval     "Eval the given forms in the project JVM."
   'stop     "Stop cake jvm processes."
   'start    "Start cake jvm processes."
   'restart  "Restart cake jvm processes."
   'reload   "Reload any .clj files that have changed or restart."
   'ps       "List running cake jvm processes for all projects."
   'kill     "Kill running cake jvm processes. Use -9 to force or --all for all projects."})

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
        (binding [*current-task* name]
          (doseq [action (:actions task)] (action)))
        (set! run? (assoc run? name true))))))

(defmacro invoke [name]
  `(run-task '~name))

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

(def *bake-port* nil)

(defn bake* [ns-forms bindings body]
  (if (nil? *bake-port*)
    (println "bake not supported. perhaps you don't have a project.clj")
    (let [ns     (symbol (str "bake.task." (name *current-task*)))
          body  `(~'let ~(quote-if odd? bindings) ~@body)
          socket (bake-connect (int *bake-port*))
          reader (BufferedReader. (InputStreamReader. (.getInputStream socket)))
          writer (OutputStreamWriter. (.getOutputStream socket))]
      (doto writer
        (.write (prn-str [ns ns-forms *command-line-args* *opts* *pwd* *env* body]))
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
  (let [[ns-forms [bindings & body]] (split-with (complement vector?) forms)]
    `(bake* '~ns-forms ~(quote-if even? bindings) '~body)))

(def *readline-marker* nil)

(defn process-command [[task args port pwd env]]
  (binding [*command-line-args* args
            *opts*              (parse-opts (keyword task) args)
            *bake-port*         port            
            *pwd*               pwd
            *env*               env
            *readline-marker*   (read)
            run?                {}]
    (ant/in-project
     (doseq [dir ["lib" "classes" "build"]]
       (.mkdirs (file dir)))
     (run-task (symbol (or task 'default))))))

(defn task-file? [file]
  (some (partial re-matches #".*\(deftask .*|.*\(defproject .*")
        (line-seq (BufferedReader. (FileReader. file)))))

(defn skip-task-files [load-file]
  (fn [file]
    (if (task-file? file)
      (println "reload-failed: unable to reload file with deftask or defproject in it:" file)
      (load-file file))))

(defn reload-files []
  (binding [clojure.core/load-file (skip-task-files load-file)]
    (server/reload-files)))

(defn prompt-read [prompt & opts]
  (let [opts (apply hash-map opts)
        echo (if (false? (:echo opts)) "@" "")]
    (println (str echo *readline-marker* prompt))
    (read-line)))

(defn start-server [port]
  (cake.project/init "project.clj" "tasks.clj")
  (let [global-project (File. (System/getProperty "user.home") ".cake")]
    (when-not (= (.getPath global-project) (System/getProperty "cake.project"))
      (cake.project/init (.getPath (File. global-project "tasks.clj")))))
  (when-not *project* (require '[cake.tasks help new]))
  (when (= "global" (:artifact-id *project*))
    (undeftask clean compile test autotest jar uberjar war uberwar install release)
    (require '[cake.tasks new]))
  (server/redirect-to-log ".cake/cake.log")
  (server/create port process-command :reload reload-files)
  nil)
