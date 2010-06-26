(defproject test-example "0.1.0-SNAPSHOT"
  :description "cake example project"
  :tasks [foo :exclude [uberjar jar]]
  :dependencies [[clojure "1.2.0-master-SNAPSHOT"]
                 [clojure-contrib "1.2.0-SNAPSHOT"]
                 [clojure-useful "0.2.2" :exclusions [clojure]]
                 [swank-clojure "1.2.1"]
                 [tokyocabinet "1.23-SNAPSHOT"]]
  :dev-dependencies [[clojure-complete "0.1.0" :exclusions [clojure]]])

(deftask bar
  (bake (:use useful) []
        (println "bar!")
        (verify true "true is false!")))
