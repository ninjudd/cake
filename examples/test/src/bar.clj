(ns bar
  (:refer-clojure :exclude [inc]))

(defn foo []
  (println "this is a bar, FOO!"))

(defn bar []
  8)

(defn bar-inc [n]
  (+ 2 n))