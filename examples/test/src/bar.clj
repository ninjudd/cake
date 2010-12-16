(ns bar
  (:refer-clojure :exclude [inc]))

(defn foo []
  (println "this is a bar, FOO!"))

(defn inc [n]
  (+ 2 n))