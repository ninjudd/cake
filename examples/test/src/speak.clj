(ns speak
  (:gen-class))

(defn sayhi []
  (println "hi!"))

(defn -main []
  (sayhi))