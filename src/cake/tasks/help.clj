(ns cake.tasks.help
  (:use cake))

(def line "-------------------------------------------")

(defn print-task [name deps docs]
  (println line)
  (let [deps (if (seq deps) (cons "=>" deps) deps)]
    (apply println "cake" name deps)
    (doseq [doc docs] (println "  " doc))))

(defn task-doc [& syms]
  (doseq [sym syms]
    (if-let [task (@tasks sym)]
      (print-task sym (:deps task) (:doc task))
      (if-let [doc (implicit-tasks sym)]
        (print-task sym [] doc)
        (println "unknown task:" sym)))))

(def system-tasks '#{stop start restart reload ps kill})

(defn taskdocs []
  (into implicit-tasks
        (for [[sym task] @tasks :when (and (not= 'default sym) (or (:a *opts*) (seq (:doc task))))]
          [sym (:doc task)])))

(defn list-all-tasks []
  (let [taskdocs (taskdocs)
        width    (apply max (map #(count (name (first %))) taskdocs))
        taskdoc  (str "cake %-" width "s  ;; %s")]
    (println line)
    (doseq [[name doc] (sort-by first taskdocs)]
      (when-not (system-tasks name)
        (println (format taskdoc name (first doc)))))
    (println)
    (println "-- system tasks ---------------------------")
    (doseq [name (sort system-tasks)]
      (println (format taskdoc name (first (taskdocs name)))))))

(deftask help
  "Print tasks with documentation. Use 'cake help TASK' for more details."
  "Use -s to list system tasks and -a to list all tasks, including those without documentation."
  (if-let [names (:help *opts*)]
    (apply task-doc (map symbol names))
    (list-all-tasks)))

(deftask default #{help})