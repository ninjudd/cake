(ns cake.tasks.defaults
  (:use cake)
  (:require cake.tasks.jar
            cake.tasks.test
            cake.tasks.compile
            cake.tasks.dependencies
            cake.tasks.swank))

(defn task-doc
  "Print documentation for a task."
  [task]
  (println "-------------------------")
  (println "cake" (name task) " ;" (:doc (@tasks task))))

(def implicit-taskdocs
  [["repl"       "Start an interactive shell."]
   ["stop/reset" "Stop cake and project jvm processes."]])

(deftask help
  "Print tasks with documentation (use -a for all tasks)."
  (let [all?     (:a opts)
        taskdocs (for [[sym task] @tasks :when (or all? (seq (:doc task)))]
                   [(name sym) (str (first (:doc task)))])
        taskdocs (into implicit-taskdocs taskdocs)
        width  (apply max (map #(count (first %)) taskdocs))
        format (str "cake %-" width "s  ;; %s\n")]
    (println (prn-str taskdocs))
    (doseq [[name doc] (sort-by first taskdocs)]
      (printf format name doc))
    (flush)))
