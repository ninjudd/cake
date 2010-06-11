(ns bar
  (:refer-clojure :exclude [inc]))

(defn foo []
  (println "this is a bar, foo!"))

(defn inc [n]
  (+ 2 n))