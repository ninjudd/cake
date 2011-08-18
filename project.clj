(defproject cake "0.6.4-SNAPSHOT"
  :description "Save your fork, there's cake!"
  :dependencies [[clojure "1.2.0"]
                 [clojure-contrib "1.2.0"]
                 [uncle "0.2.3"]
                 [depot "0.1.4"]
                 [classlojure "0.5.2"]
                 [useful "0.6.0"]
                 [org.clojure/tools.namespace "0.1.1" :exclusions [org.clojure/java.classpath]]
                 [org.clojars.ninjudd/java.classpath "0.1.2-SNAPSHOT"]
                 [com.jcraft/jsch "0.1.42"]]
  :dev-dependencies [[org.clojars.flatland/cake-marginalia "0.6.2"]]
  :tasks [cake-marginalia.tasks]
  :copy-deps true)
