(ns cake.tasks.run
  (:use cake cake.core)
  (:import [java.io File]))

(deftask run
  "Execute a script in the project jvm."
  (let [script (first (*opts* :run))]
    (bake [script (if (.isAbsolute (File. script)) script (str *pwd* "/" script))]
       (load-file script))))