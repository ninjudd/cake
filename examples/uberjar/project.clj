(defproject uberjar "0.0.1-SNAPSHOT"
  :description "Test an uberjar with a main class."
  :dependencies [[clojure "1.2.0"]
                 [clojure-contrib "1.2.0"]
                 [clojure-csv/clojure-csv "1.1.0"]]
  :warn-on-reflection true
  ;; :omit-source true
  :main uberjar)
