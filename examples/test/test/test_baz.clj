(ns test-baz
  (:use cake clojure.test))

(deftest #^{:tags [:baz]} baz
  (println ":baz:")
  (is (= 1 1)))