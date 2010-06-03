(ns test-servlet
  (:use clojure.test))

(deftest test-example
  (println "running test-example...")
  (is true)
  (is true)
  (is false))

(deftest two-more-asserts
  (is true)
  (is false))