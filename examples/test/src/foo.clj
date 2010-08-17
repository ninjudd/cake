(ns foo
  (:use cake cake.core))

(defn bar []
  [(count *project*) (keys *project*)])

(deftask foo
  (bake
    (:use clojure.test)
    [a (bar)]
      (is true)
      (println "============" (.getName *ns*) "============")
      (println a)
      (println *project*)
      (println *opts*)))
