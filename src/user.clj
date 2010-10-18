(ns user
  (:require cake.server))

(try
  (defn pst []
    (cake.server/print-stacktrace *e))
  (catch Exception e))