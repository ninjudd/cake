(ns test-test
  (:use clojure.test cake))

(declare *once* *every*)

(defn run-once [f]
  (binding [*once* true] 
    (f)))

(use-fixtures :once run-once)

(deftest fixtures-test
  (testing "once"
    (is *once*)))
