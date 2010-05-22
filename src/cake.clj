(ns cake
  (:require cake.project))

(def tasks (atom {}))

(defn update [m k f & args]
  (assoc m k (apply f (get m k) args)))

(defn cat [s1 s2]
  (if s1 (str s1 " " s2) s2))

(defn add-form [task form]
  (let [task (or task {:actions [] :deps []})]
    (cond
     (symbol? form) (update task :deps    conj form)
     (string? form) (update task :doc     cat  form)
     (list?   form) (update task :actions conj form)
     :else (throw (IllegalArgumentException.
                   "deftask requires forms to be a symbol, string or list")))))

(defmacro abort-if [pred message]
  `(when ~pred
     (println ~message)
     (System/exit 0)))

(defmacro deftask
  "Define a cake task. Forms can be any of the following:
   string - documentation to append to the task definition
   symbol - the name of another task that is a prerequisite for this task
   list   - code to be run when the task is executed"
  [name params & forms]
  `(swap! tasks update '~name (partial reduce add-form) '~forms))

(defn run-task
  ([name] (swap! tasks run-task name))
  ([tasks name]
     (let [task (tasks name)]
       (abort-if (nil? task) (str "Unknown task: " name))
       (if (:results task)
         tasks
         (let [tasks (reduce run-task tasks (task :deps))]
           (assoc-in tasks [name :results]
             (doall (map eval (task :actions)))))))))

(defmacro task-doc [task]
  (println "-------------------------")
  (println "cake" (name task) " ;" (:doc (@tasks task))))

(defn -main []
  (cake.project/init)
  (let [[task & args] *command-line-args*]
    (run-task (symbol (or task 'default))))
  nil)