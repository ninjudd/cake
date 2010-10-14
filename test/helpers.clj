(ns helpers
  (:use cake
        [cake.file :only [file]]
        [clojure.java.shell :only [sh with-sh-dir]])
  (:require [cake.ant :as ant]))

(defn in-test-project [f]
  (binding [*root* (file "examples/test")]
    (ant/in-project (f))))

(defn cake [& args]
  (with-sh-dir *root*
    (apply sh *script* args)))
