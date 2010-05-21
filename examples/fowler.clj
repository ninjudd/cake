(deftask code-gen []
  "This task generates code. It has no dependencies."
  (println "generating code..."))

(deftask compile [] code-gen
  "This task does the compilation. It depends on code-gen."
  (println "compiling..."))

(deftask data-load [] code-gen
  "This task loads the test data. It depends on code-gen."
  (println "loading test data..."))

(deftask test [] compile data-load
  "This task runs the tests. It depends on compile and data-load."
  (println "running tests..."))

(dotask test)