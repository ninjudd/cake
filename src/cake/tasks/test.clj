(ns cake.tasks.test
  (:use cake
        cake.ant
        [clojure.contrib.find-namespaces :only [find-namespaces-in-dir]])
  (:import [org.apache.tools.ant.taskdefs Java]
           [java.io File]))

(comment defmacro bench [expr]
  "from stuart halloway's programming clojure, pg 205- http://github.com/stuarthalloway/programming-clojure/blob/master/examples/macros.clj"
  `(let [start# (System/nanoTime)
         result# ~expr]
     {:result result# :elapsed (- (System/nanoTime) start#)}))

(defmacro bench [expr]
  `(let [~'start (System/nanoTime)
         ~'result ~expr]
     {:result ~'result :elapsed (- (System/nanoTime) ~'start)}))

(deftask test
  (println "opts:" opts)
  (let [to-test (find-namespaces-in-dir (File. (:root project) "test"))]
    (bake `(do
             (require 'clojure.test)
             (doseq [ns# '~to-test]
               (require ns#))
             (let [start#      (System/nanoTime)]
               (apply clojure.test/run-tests '~to-test)
               (println "Finished in" (/ (- (System/nanoTime) start#) (Math/pow 10 9)) "seconds.\n"))))))


