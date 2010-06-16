(ns cake)

; must be declared first so other namespaces can access them
(def current-task nil)
(defonce cake-project (atom nil))
(def project nil)

(ns cake
  (:use clojure.useful
        [cake.project :only [init]])
  (:require [cake.server :as server]
            [cake.swank :as swank]
            [cake.ant :as ant])
  (:import [java.io File FileReader InputStreamReader OutputStreamWriter BufferedReader]
           [java.net Socket ConnectException]))

(defn verbose? [opts]
  (or (:v opts) (:verbose opts)))

(defn group [project]
  (if (or (= project 'clojure) (= project 'clojure-contrib))
    "org.clojure"
    (or (namespace project) (name project))))

(defmacro defproject [project-name version & args]
  (let [root (.getParent (File. *file*))
        artifact (name project-name)
        opts (apply hash-map args)
        [tasks task-opts] (split-with symbol? (:tasks opts))
        task-opts (apply hash-map task-opts)]
    `(do (compare-and-set! cake-project nil
           (-> '~opts
               (assoc :artifact-id ~artifact
                      :group-id    ~(group project-name)
                      :root        ~root
                      :version     ~version
                      :swank      '~(swank/config))
               (assoc-or :name ~artifact)))
         ; @cake-project must be set before we include the tasks for bake to work.
         (require 'cake.tasks.defaults)
         ~@(for [ns tasks] `(require '~ns))
         (undeftask ~@(:exclude task-opts)))))

(defn file-name [& path]
  (let [path (if (instance? File (first path))
               (cons (.getName (first path)) (rest path))
               (cons (:root project) path))]
    (apply str (interpose (File/separator) path))))

(defn file [& path]
  (File. (apply file-name path)))

(defn dependency? [form]
  (or (symbol? form)
      (and (seq? form)
           (include? (first form) ['fn* 'fn]))))

(defn update-task [task deps doc actions]
  {:pre [(every? dependency? deps) (every? fn? actions)]}
  (let [task (or task {:actions [] :deps [] :doc []})]
    (-> task
        (update :deps    into deps)
        (update :doc     into doc)
        (update :actions into actions))))

(defonce tasks (atom {}))
(def run? nil)

(def implicit-tasks
  {'repl    "Start an interactive shell."
   'stop    "Stop cake and project jvm processes."
   'start   "Start cake and project jvm processes."
   'restart "Restart cake and project jvm processes."
   'reload  "Reload any .clj files that have changed or restart."})

(defmacro deftask
  "Define a cake task. Each part of the body is optional. Task definitions can
   be broken up among multiple deftask calls and even multiple files:
   (deftask foo => bar, baz ; => followed by prerequisites for this task
     \"Documentation for task.\"
     (do-something)
     (do-something-else))"
  [name & body]
  (verify (not (implicit-tasks name)) (format "Cannot redefine %s task" name))
  (let [[deps body] (if (= '=> (first body))
                      (split-with dependency? (rest body))
                      [() body])
        [doc actions] (split-with string? body)
        actions (vec (map #(list 'fn [] %) actions))]
    `(swap! tasks update '~name update-task '~deps '~doc ~actions)))

(defmacro undeftask [& names]
  `(swap! tasks dissoc ~@(map #(list 'quote %) names)))

(defn run-task
  "Execute the specified task after executing all prerequisite tasks."
  [form]
  (if (list? form)
    (binding [project @cake-project] ((eval form))) ; excute anonymous dependency
    (let [name form, task (@tasks name)]
      (if (nil? task)
        (println "unknown task:" name)
        (when-not (run? task)
          (doseq [dep (:deps task)] (run-task dep))
          (binding [current-task name]
            (doseq [action (:actions task)] (action)))
          (set! run? (assoc run? name true)))))))

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
          forms `[(~'ns ~ns ~@ns-forms) (~'let ~(quote-if odd? bindings) ~@body)]
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
        bindings (into ['opts 'cake/opts, 'project 'cake/project] bindings)]
    `(bake* '~ns-forms ~(quote-if even? bindings) '~body)))

(def opts nil)

(defn process-command [form]
  (let [[task args port] form]
    (binding [project @cake-project
              ant/ant-project (ant/init-project project server/*outs*)
              opts (parse-opts (keyword task) (map str args))
              bake-port port
              run? {}]
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

(defn start-server [port]
  (init)
  (server/create port process-command :reload reload-files)
  nil)
