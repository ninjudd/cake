(deftask code-gen []
  (println "do the code generation"))

(deftask compile [] code-gen
  (println "do the compilation"))

(deftask data-load [] code-gen
  (println "load the test data"))

(deftask test [] compile data-load
  (println "run the tests"))

(dotask test)