(defproject test-example "0.1.0-SNAPSHOT"
  :description "cake example project"
  :tasks [foo :exclude [uberjar jar]]
  :dependencies [[clojure "1.2.0-master-SNAPSHOT"]
                 [clojure-contrib "1.2.0-SNAPSHOT"]
                 [clojure-useful "0.1.5"]])