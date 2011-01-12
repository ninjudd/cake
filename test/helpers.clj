(ns helpers
  (:use cake
        [cake.file :only [file with-root]]
        [uncle.core :only [in-project]]
        [clojure.java.shell :only [sh with-sh-dir]]))

(defn in-test-project [f]
  (with-root (file "examples/test")
    (in-project *outs* (f))))

(defn cake [& args]
  (with-sh-dir *root*
    (apply sh *script* args)))
