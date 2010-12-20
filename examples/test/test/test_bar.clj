(ns test-bar
  (:use bar clojure.test))

(deftest test-foo
  (is (= 8 (bar))))
