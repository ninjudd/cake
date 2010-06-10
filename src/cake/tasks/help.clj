(ns cake.tasks.help
  (:use cake))

(deftask help
  "Print tasks with documentation (use -a for all tasks)."
  (println "-------------------------------------------")
  (let [all?     (:a opts)
        taskdocs (for [[sym task] @tasks :when (and (not= 'default sym) (or all? (seq (:doc task))))]
                   [sym (str (first (:doc task)))])
        taskdocs (into implicit-tasks taskdocs)
        width  (apply max (map #(count (name (first %))) taskdocs))
        format (str "cake %-" width "s  ;; %s\n")]
    (doseq [[name doc] (sort-by first taskdocs)]
      (printf format name doc))
    (flush)))