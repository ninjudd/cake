(ns cake.leiningen
  (:use [leiningen.core :only [resolve-task]]))

(defmacro lein [task & args]
  `((resolve-task ~(name task)) ~@args))
