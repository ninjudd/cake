(defproject test-example "0.1.0-SNAPSHOT"
  :description "cake example project"
  :dependencies [[clojure "1.2.0-master-SNAPSHOT"]
                 [clojure-contrib "1.2.0-SNAPSHOT"]
                 [clojure-useful "0.1.5-SNAPSHOT"]])


(deftask foo
  (bake []
   (println project)
   (println opts)))
