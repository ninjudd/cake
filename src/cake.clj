(ns cake
  (:require cake.project)
  (:use clojure.useful))

(def tasks (atom {}))

(def opts
  (let [task (first *command-line-args*)]
    (parse-args (when task (keyword task))
                (next *command-line-args*))))

(defn cat [s1 s2]
  (if s1 (str s1 " " s2) s2))

(defn update-task [task deps doc actions]
  {:pre [(every? symbol? deps) (every? fn? actions)]}
  (let [task (or task {:actions [] :deps []})]
    (-> task
        (update :deps    into deps)
        (update :doc     cat  doc)
        (update :actions into actions))))

(def project (atom nil))

(defmacro defproject [project-name version & args]
  (let [root (.getParent (java.io.File. *file*))]
    `(do (require 'cake.tasks.dependencies)
         (compare-and-set! project nil
           (-> (apply hash-map '~args)
               (assoc :name ~(name project-name)
                      :group ~(or (namespace project-name)
                                  (name project-name))
                      :root ~root
                      :version ~version)
               (assoc-or :library-path (str ~root "/lib")))))))

(defmacro deftask
  "Define a cake task. Each part of the body is optional. Task definitions can
   be broken up among multiple deftask calls and even multiple files:
   (deftask foo => bar, baz ; => followed by prerequisites for this task
     \"Documentation for task.\"
     (do-something)
     (do-something-else))"
  [name & body]
  (let [[deps body] (if (= '=> (first body))
                      (split-with symbol? (rest body))
                      [() body])
        [doc actions] (if (string? (first body))
                        (split-at 1 body)
                        [nil body])
        actions (vec (map #(list 'fn [] %) actions))]
    `(swap! tasks update '~name update-task '~deps '~doc ~actions)))

(defn run-task
  "Execute the specified task after executing all prerequisite tasks."
  ([name] (swap! tasks run-task name) nil)
  ([tasks name]
     (let [task (tasks name)]
       (when (nil? task) (abort "Unknown task:" name))
       (if (:results task)
         tasks
         (let [tasks (reduce run-task tasks (task :deps))]
           (assoc-in tasks [name :results]
             (doseq [action (task :actions)]
               (binding [project @project]
                 (action)))))))))

(defmacro task-doc [task]
  "Print documentation for a task."
  (println "-------------------------")
  (println "cake" (name task) " ;" (:doc (@tasks task))))

(defn -main []
  (cake.project/init)
  (let [task (first *command-line-args*)]
    (run-task (symbol (or task 'default)))))
