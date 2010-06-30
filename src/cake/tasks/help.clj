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
        (print-task sym [] [doc])
        (println "unknown task:" sym)))))

(defn taskdocs [all?]
  (into implicit-tasks
        (for [[sym task] @tasks :when (and (not= 'default sym) (or all? (seq (:doc task))))]
          [sym (str (first (:doc task)))])))

(defn list-all-tasks []
  (println line)
  (let [taskdocs (taskdocs (:a opts))
        width    (apply max (map #(count (name (first %))) taskdocs))
        taskdoc  (str "cake %-" width "s  ;; %s")]
    (doseq [[name doc] (sort-by first taskdocs)]
      (println (format taskdoc name doc)))))

(deftask help
  "Print tasks with documentation. Use 'cake help TASK' for more details."
  "You can use 'cake help -a' to list all tasks, including those without documentation."
  (if-let [names (:help opts)]
    (apply task-doc (map symbol names))
    (list-all-tasks)))

(deftask version
  "Print the current project name and version."
  (println (:artifact-id project) (:version project)))
