(ns cake.tasks.check
  (:use cake cake.core
        [cake.file :only [file rmdir]]
        [cake.classloader :only [reset-classloaders!]]))

(deftask check #{compile-java}
  "Check syntax and warn on reflection."
  (reset-classloaders!)
  (bake (:use [bake.core :only [log print-stacktrace]]
              [bake.find-namespaces :only [find-clojure-sources-in-dir
                                           read-file-ns-decl]]
              [bake.nsdeps :only [deps-from-ns-decl]]) []
              (binding [*warn-on-reflection* true]
                (doseq [src-file (mapcat #(find-clojure-sources-in-dir
                                           (java.io.File. %))
                                         (:source-path *project*))]
                  (let [depends-on-cake-core?
                        (fn [ns-decl]
                          (contains? (deps-from-ns-decl ns-decl) 'cake.core))
                        src-file-ns-decl (read-file-ns-decl src-file)]
                    ;; Skip any namespaces that depend on cake.core, as they
                    ;; will fail.
                    (if (not (depends-on-cake-core? src-file-ns-decl))
                      (do (log "Compiling namespace " (second src-file-ns-decl))
                          (try
                            (load-file (.getPath src-file))
                            (catch ExceptionInInitializerError e
                              (print-stacktrace e))))
                      (log "Skipping task namespace:" (second src-file-ns-decl)))))))
  (reset-classloaders!))