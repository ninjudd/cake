(ns foo
  (:use cake))

(defn bar [project]
  [(count project) (keys project)])

(deftask foo
  (bake
    (:use clojure.test)
    [a (bar project)]
      (is true)
      (println "============" (.getName *ns*) "============")
      (println a)
      (println project)
      (println opts)))
