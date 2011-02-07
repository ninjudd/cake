(ns cake.tasks.check
    "Check syntax and warn on reflection."
  (:use cake
        cake.core
        [cake.file :only [file rmdir]]
        [cake.tasks.compile :only [source-dir]]))

(deftask check
  "Check syntax and warn on reflection."
  (let [source-path (source-dir)]
    (bake (:use [bake.core :only [log print-stacktrace]]
                [bake.find-namespaces :only [find-clojure-sources-in-dir
                                             read-file-ns-decl]])
          [source-path source-path] ;; Just copy the var over to bake.
          (binding [*warn-on-reflection* true]
            (doseq [src-file (find-clojure-sources-in-dir source-path)]
              (log "Compiling namespace " (second (read-file-ns-decl src-file)))
              (try
                (load-file (.getPath src-file))
                (catch ExceptionInInitializerError e
                  (print-stacktrace e))))))))