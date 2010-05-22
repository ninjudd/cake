(ns cake
  (:require cake.project)
  (:use clojure.useful))

(def tasks (atom {}))

(let [m (parse-args (next *command-line-args*))]
  (def args (or (m nil) []))
  (def opts (dissoc m nil)))

(defn cat [s1 s2]
  (if s1 (str s1 " " s2) s2))

(defn update-task [task deps doc actions]
  {:pre [(every? symbol? deps) (every? list? actions)]}
  (let [task (or task {:actions [] :deps []})]
    (-> task
        (update :deps    into deps)
        (update :doc     cat  doc)
        (update :actions into actions))))

(defmacro defproject [name version & args]
  "nothing to see here")

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
                        [nil body])]
    `(swap! tasks update '~name update-task '~deps '~doc '~actions)))

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
             (doall (map eval (task :actions)))))))))

(defmacro task-doc [task]
  "Print documentation for a task."
  (println "-------------------------")
  (println "cake" (name task) " ;" (:doc (@tasks task))))

(defn -main []
  (cake.project/init)
  (let [task (first *command-line-args*)]
    (run-task (symbol (or task 'default)))))
