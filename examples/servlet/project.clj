(defproject servlet "0.1.0-SNAPSHOT"
  :description "cake servlet example"
  :dependencies [[org.clojure/clojure "1.2.0-master-SNAPSHOT"]
                 [org.clojure/clojure-contrib "1.2.0-SNAPSHOT"]
                 [clojure-useful "0.1.1-SNAPSHOT"]
                 [jline "0.9.94"]]
  :dev-dependencies [[swank-clojure "1.2.1"]]
  :aot [servlet]
  :war-files [["src/database.properties" "WEB-INF/classes/db.properties"]]
  :jar-files [["src/database.properties" "db.properties"]
              "project.clj"])
