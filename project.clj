(defproject cake "0.7.0"
  :description "Save your fork, there's cake!"
  :dependencies [[clojure "1.2.0"]
                 [uncle "0.2.3"]
                 [depot "0.1.8"]
                 [classlojure "0.6.3"]
                 [useful "0.8.0-alpha1"]
                 [slingshot "0.7.2"]
                 [org.clojure/tools.namespace "0.1.1" :exclusions [org.clojure/java.classpath]]
                 [org.clojars.ninjudd/java.classpath "0.1.2-SNAPSHOT"]
                 [org.clojars.ninjudd/data.xml "0.0.1-SNAPSHOT"]
                 [com.jcraft/jsch "0.1.42"]
                 [difform "1.1.2"]
                 [org.clojars.rosejn/clansi "1.2.0-SNAPSHOT" :exclusions [org.clojure/clojure]]
                 [clj-stacktrace "0.2.3"]
                 [sherlock "0.1.0-alpha3"]]
  :copy-deps true)
