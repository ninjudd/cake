(ns cake.tasks.swank
  (:use cake))

(deftask swank
  (bake (:require [swank.swank :as swank]) []
        (println "starting swank")
        (println (seq (.getURLs (java.lang.ClassLoader/getSystemClassLoader))))
        (swank/start-repl)))

