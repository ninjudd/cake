(defproject uberjar "0.0.1-SNAPSHOT"
  :description "Test an uberjar with a main class."
  :dependencies [[clojure "1.2.0"]
                 [clojure-contrib "1.2.0"]
                 [clojure-csv/clojure-csv "1.1.0"]]
  :main uberjar)

(defcontext dev
  :foo 1
  :bar 2)

(defcontext qa
  :foo 10
  :bar 20)

(defcontext production
  :foo 100
  :bar 200)