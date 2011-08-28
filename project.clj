(defproject cake "0.6.4-SNAPSHOT"
  :description "Save your fork, there's cake!"
  :dependencies [[clojure "1.2.0"]
                 [clojure-contrib "1.2.0"]
                 [uncle "0.2.3"]
                 [depot "0.1.5"]
                 [classlojure "0.5.5"]
                 [useful "0.7.0-alpha3"]
                 [org.clojure/tools.namespace "0.1.1" :exclusions [org.clojure/java.classpath]]
                 [org.clojars.ninjudd/java.classpath "0.1.2-SNAPSHOT"]
                 [com.jcraft/jsch "0.1.42"]
                 [difform "1.1.1"]
                 [org.clojars.rosejn/clansi "1.2.0-SNAPSHOT" :exclusions [org.clojure/clojure]]]
  :dev-dependencies [[org.clojars.flatland/cake-marginalia "0.6.3"]]
  :tasks [cake-marginalia.tasks]
  :copy-deps true)
