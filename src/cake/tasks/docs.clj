(ns cake.tasks.docs
  (:use cake
        [clojure.string :only [join]]
        [bake.core :only [log]]
        (cake [core :only [deftask]]
              [task :only [tasks]])))

(deftask gen-docs #{deps}
  "Generate documentation for tasks."
  "If a filename is passed, documentation is written to that file. Otherwise,
it is written to tasks.md in the current directory."
  {[file] :task-docs}
  (let [file (or file "tasks.md")]
    (log (str "Generating documentation in " file "."))
    (spit
     file
     (join
      (for [[k {:keys [docs deps]}] tasks]
        (str "#### " k "\n\n"
             (when (seq docs) (str (join "\n" docs) "\n\n"))
             (when (seq deps) (str "dependencies: " deps "\n\n"))))))))