(ns cake
  (:use clojure.useful
        [cake.project :only [init]]))

(def tasks (atom {}))

(def opts
  (let [task (first *command-line-args*)]
    (parse-args (when task (keyword task))
                (next *command-line-args*))))

(defn verbose? [opts]
  (or (:v opts) (:verbose opts)))

(defn group [project]
  (if (or (= project 'clojure) (= project 'clojure-contrib))
    "org.clojure"
    (or (namespace project) (name project))))

(def cake-project (atom nil))

(defmacro defproject [project-name version & args]
  (let [root (.getParent (java.io.File. *file*))
        artifact (name project-name)]
    `(do (require 'cake.ant)
         (cake.ant/init-project ~root)
         (compare-and-set! cake-project nil
           (-> (apply hash-map '~args)
               (assoc :artifact-id ~artifact
                      :group-id    ~(group project-name)
                      :root        ~root
                      :version     ~version)
               (assoc-or :name         ~artifact
                         :library-path   (str ~root "/lib")
                         :compile-path   (str ~root "/classes")
                         :resources-path (str ~root "/resources")
                         :source-path    (str ~root "/src")
                         :test-path      (str ~root "/test"))))
         ; @cake-project must be set before we include the tasks for bake to work.
         (require 'cake.tasks.defaults))))

(defn dependency? [form]
  (or (symbol? form)
      (and (seq? form)
           (include? (first form) ['fn* 'fn]))))

(defn cat [s1 s2]
  (if s1 (str s1 " " s2) s2))

(defn update-task [task deps doc actions]
  {:pre [(every? dependency? deps) (every? fn? actions)]}
  (let [task (or task {:actions [] :deps [] :run? (atom false)})]
    (-> task
        (update :deps    into deps)
        (update :doc     cat  doc)
        (update :actions into actions))))

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

(def project nil)
(def current-task nil)

(defn run-task
  "Execute the specified task after executing all prerequisite tasks."
  [form]
  (if (list? form)
    (binding [project @cake-project] ((eval form))) ; excute anonymous dependency
    (let [name form, task (@tasks name)]
      (when (nil? task) (abort "Unknown task:" name))
      (when-not @(:run? task)
        (doseq [dep (:deps task)] (run-task dep))
        (doseq [action (:actions task)]
          (binding [project @cake-project, current-task name]
            (action)))
        (compare-and-set! (get-in @tasks [name :run?]) false true)))))

(defmacro bake [bindings & body]
  "Execute body in a fork of the jvm with the classpath of your project."
  (let [code (prn-str `(do
                         (let ~bindings 
                           (def ~'project '~(deref cake-project))
                           (def ~'opts ~opts)
                           ~@body)))]
    `(do (require 'cake.ant)
         (cake.ant/ant org.apache.tools.ant.taskdefs.Java
           {:classname   "clojure.main"
            :classpath   (cake.ant/classpath project (:test-path project) (System/getProperty "bakepath"))
            :fork        true
            :failonerror true}
           (cake.ant/args ["-e" ~code])))))

(defmacro task-doc [task]
  "Print documentation for a task."
  (println "-------------------------")
  (println "cake" (name task) " ;" (:doc (@tasks task))))

(defn -main []
  (init)
  (let [task (first *command-line-args*)]
    (run-task (symbol (or task 'default)))
    (System/exit 0)))
