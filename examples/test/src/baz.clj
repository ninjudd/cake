(ns baz
  (:use [bar :only [foo]])
  (:require bar))

(defn baz [i]
  (foo)
  (bar/inc i))
