(ns foo
  (:use clojure.test))

(deftest ^{:tags [:a :b]} bar
         (println "bar ran!")
         (is true))

(deftest ^{:tags [:a]} baz
  (println "baz ran!")
  (is true))