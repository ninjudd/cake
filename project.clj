(defproject cake "0.2.4-SNAPSHOT" :aot :all
  :description "Save your fork, there's cake!"
  :dependencies [[org.clojure/clojure "1.2.0-master-SNAPSHOT"]
                 [org.clojure/clojure-contrib "1.2.0-SNAPSHOT"]
                 [clojure-useful "0.1.4-SNAPSHOT"]
                 [org.apache.ant/ant "1.8.1"]
                 [org.apache.ant/ant-jsch "1.8.1"]
                 [org.clojars.ninjudd/maven-ant-tasks "2.1.0" :exclusions [ant/ant]]
                 [org.apache.maven.plugins/maven-shade-plugin "1.3.3"]])