(ns foo
  (:use bake))

(defn bar [project]
  [(count project) (keys project)])

(deftask foo
  (cake
    (:use clojure.test)
    [a (bar project)]
      (is true)
      (println "============" (.getName *ns*) "============")
      (println a)
      (println project)
      (println opts)))
