(ns test-test
  (:use clojure.test))

(deftest #^{:tags [:foo]} tag-foo
  (comment println "tag :foo")
  (is (= 1 1)))

(deftest #^{:tags [:bar]} tag-bar
  (comment println "tag :bar")
  (is (= 1 1)))

(deftest #^{:tags [:foo :bar]} tag-foo-and-bar
  (comment println "tags [:foo :bar]")
  (is (= 1 1)))
