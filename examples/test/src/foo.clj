(ns foo
  (:use cake cake.core))

(defn bar []
  [(count *project*) (keys *project*)])

(deftask foo
  (prn "foo.clj")
  (bake
    (:use clojure.test)
    [a (bar)]
      (is true)
      (println "============" (.getName *ns*) "============")
      (println a)
      (println *project*)
      (println *opts*)))
