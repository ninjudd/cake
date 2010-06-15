(ns servlet
  (:gen-class))

(defn speak [& _]
  (println "woof!"))

(speak)