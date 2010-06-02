(ns cake.tasks.test
  (:use cake
        cake.ant
        [clojure.contrib.find-namespaces :only [find-namespaces-in-dir]])
  (:import [org.apache.tools.ant.taskdefs Java]
           [java.io File]))

(deftask test
  (let [to-test (find-namespaces-in-dir (File. (:root project) "test"))]
    (bake `(do
             (require 'clojure.test)
             (doseq [ns# '~to-test]
               (require ns#)
               (clojure.test/run-tests ns#))))))

