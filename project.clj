(defproject cake "0.2.6"
  :description "Save your fork, there's cake!"
  :dependencies [[org.clojure/clojure "1.2.0-master-SNAPSHOT"]
                 [clojure-useful "0.2.1"]
                 [jline "0.9.94"]
                 [org.apache.ant/ant "1.8.1"]
                 [org.apache.ant/ant-jsch "1.8.1"]
                 [org.clojars.ninjudd/maven-ant-tasks "2.1.0" :exclusions [ant/ant]]
                 [org.apache.maven.plugins/maven-shade-plugin "1.3.3"]])