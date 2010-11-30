(ns cake.tasks.help
  (:use cake
        [cake.core :only [deftask]]
        [cake.task :only [tasks implicit-tasks]]))

(def line "-------------------------------------------")

(defn print-task [name deps docs]
  (println line)
  (let [deps (if (seq deps) (cons "=>" deps) deps)]
    (apply println "cake" name deps)
    (doseq [doc docs] (println "  " doc))))

(def system-tasks #{"ps" "kill" "killall" "log" "upgrade"})
(def hidden-tasks #{"default" "eat" "bake"})

(defn taskdocs [pattern]
  (filter
   (fn [[name doc]]
     (and (not (contains? hidden-tasks name))
          (re-find pattern name)
          (or (:a *opts*) doc)))
   (into (for [[t doc] implicit-tasks] [(name t) doc])
         (for [taskname (keys tasks)]
           (let [task (get tasks taskname)]
             [(name taskname) (seq (:docs task))])))))

(defn list-tasks [pattern system?]
  (let [taskdocs (into {} (taskdocs pattern))]
    (when (seq taskdocs)
      (let [width    (apply max (map #(count (first %)) taskdocs))
            taskdoc  (str "cake %-" width "s  ;; %s")]
        (println line)
        (doseq [[name doc] (sort-by first taskdocs)]
          (when-not (system-tasks name)
            (println (format taskdoc name (or (first doc) "")))))
        (when system?
          (println)
          (println "-- system tasks ---------------------------")
          (doseq [name (sort system-tasks)]
            (println (format taskdoc name (first (taskdocs name))))))))))

(defn task-doc [& task-names]
  (doseq [name task-names :let [sym (symbol name)]]
    (if-let [task (get tasks sym)]
      (print-task name (:deps task) (:docs task))
      (if-let [doc (implicit-tasks sym)]
        (print-task name [] doc)))
    (list-tasks (re-pattern name) false)))

(deftask help
  "Print tasks with documentation. Use 'cake help TASK' for more details."
  "Use -s to list system tasks and -a to list all tasks, including those without documentation."
  (if-let [tasks (:help *opts*)]
    (apply task-doc tasks)
    (list-tasks #"^[^.]*$" true)))
