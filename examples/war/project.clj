(defproject war "0.1.0-SNAPSHOT"
  :description "cake example project"
  :dependencies [[org.clojure/clojure "1.2.0-master-SNAPSHOT"]
                 [org.clojure/clojure-contrib "1.2.0-SNAPSHOT"]
                 [clojure-useful "0.1.1-SNAPSHOT"]
                 [jline "0.9.94"]]
  :dev-dependencies [[swank-clojure "1.2.1"]]
  :aot [servlet]
)

