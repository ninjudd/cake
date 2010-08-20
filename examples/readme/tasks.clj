(deftask code-gen
  "This task generates code. It has no dependencies."
  (println "generating code..."))

(deftask compile #{code-gen}
  "This task does the compilation. It depends on code-gen."
  (println "compiling..."))

(deftask data-load #{code-gen}
  "This task loads the test data. It depends on code-gen."
  (println "loading test data..."))

(deftask test #{compile data-load}
  "This task runs the tests. It depends on compile and data-load."
  (println "running tests..."))

;; You can call a task explicitly using run-task.

(deftask primary
  (println "executing primary task...")
  (when (:secondary *opts*)
    (invoke secondary)))

(deftask secondary
  (println "executing secondary task..."))

;; You to can add actions, dependencies and documentation to existing tasks.

(deftask compile-native
  (println "compiling native code..."))

(deftask compile #{compile-native}
  "Native C code will be compiled before compiling Clojure and Java code.")

(deftask test
  (println "running integration tests..."))

;; You can redefine a default task completely using undeftask.

(undeftask release)
(deftask release
  "Release code to production servers."
  (println "releasing to production..."))

;; Cake will automatically detect circular dependencies.

(deftask foo #{bar})
(deftask bar #{baz})
(deftask baz #{foo})