(ns test-example
  (:use cake clojure.test))

(deftest test-example
  (println "running test-example...")
  (is true)
  (is (= :test (:context *project*))))