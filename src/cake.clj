(ns cake)
(def current-task nil) ; current-task must be declared here so cake.ant can access it for logging

(ns cake
  (:use clojure.useful cake.server
        [cake.project :only [init]])
  (:require [cake.ant :as ant])
  (:import [java.io File InputStreamReader OutputStreamWriter BufferedReader]
           [java.net Socket ConnectException]))

(defn verbose? [opts]
  (or (:v opts) (:verbose opts)))

(defn group [project]
  (if (or (= project 'clojure) (= project 'clojure-contrib))
    "org.clojure"
    (or (namespace project) (name project))))

(def cake-project (atom nil))
(def project nil)

(defmacro defproject [project-name version & args]
  (let [root (.getParent (File. *file*))
        artifact (name project-name)]
    `(do (compare-and-set! cake-project nil
           (-> (apply hash-map '~args)
               (assoc :artifact-id ~artifact
                      :group-id    ~(group project-name)
                      :root        ~root
                      :version     ~version)
               (assoc-or :name ~artifact)))
         ; @cake-project must be set before we include the tasks for bake to work.
         (require 'cake.tasks.defaults))))

(defn file [& path]
  (File. (apply str (interpose "/" (cons (:root project) path)))))

(defn dependency? [form]
  (or (symbol? form)
      (and (seq? form)
           (include? (first form) ['fn* 'fn]))))

(defn cat [s1 s2]
  (if s1 (str s1 " " s2) s2))

(defn update-task [task deps doc actions]
  {:pre [(every? dependency? deps) (every? fn? actions)]}
  (let [task (or task {:actions [] :deps []})]
    (-> task
        (update :deps    into deps)
        (update :doc     cat  doc)
        (update :actions into actions))))

(def tasks (atom {}))
(def run? nil)

(defmacro deftask
  "Define a cake task. Each part of the body is optional. Task definitions can
   be broken up among multiple deftask calls and even multiple files:
   (deftask foo => bar, baz ; => followed by prerequisites for this task
     \"Documentation for task.\"
     (do-something)
     (do-something-else))"
  [name & body]
  (let [[deps body] (if (= '=> (first body))
                      (split-with dependency? (rest body))
                      [() body])
        [doc actions] (if (string? (first body))
                        (split-at 1 body)
                        [nil body])
        actions (vec (map #(list 'fn [] %) actions))]
    `(swap! tasks update '~name update-task '~deps '~doc ~actions)))

(defn remove-task! [name]
  (swap! tasks dissoc name))

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
      (flush))))

(defmacro bake
  "Execute code in a separate jvm with the classpath of your projects. Bindings allow passing
   state to the project jvm. Namespace forms like use and require must be specified before bindings."
  {:arglists '([ns-forms* bindings body*])}
  [& forms]
  (let [[ns-forms [bindings & body]] (split-with (complement vector?) forms)
        bindings (into ['opts 'cake/opts, 'project 'cake/project] bindings)]
    `(bake* '~ns-forms ~(quote-if even? bindings) '~body)))

(defmacro task-doc
  "Print documentation for a task."
  [task]
  (println "-------------------------")
  (println "cake" (name task) " ;" (:doc (@tasks task))))

(def opts nil)

(defn process-command [form]
  (let [[task args port] form]
    (binding [project @cake-project
              ant/ant-project (ant/init-project project *outs*)
              opts (parse-args (keyword task) (map str args))
              bake-port port
              run? {}]
      (run-task (symbol (or task 'default))))))

(defn start-server [port]
  (init)
  (create-server port process-command))
