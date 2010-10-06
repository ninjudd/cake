(ns uberjar
  (:use cake clojure-csv.core)
  (:gen-class))

(defn -main [& args]
  (println "Running main...")
  (println "args:"     (pr-str args))
  (println "context:"  (:context *project*))
  (println "*project*" (pr-str *project*)))
