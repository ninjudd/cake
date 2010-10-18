(defproject cake "0.6.0-SNAPSHOT"
  :description "Save your fork, there's cake!"
  :url "http://github.com/ninjudd/cake"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[clojure "1.2.0"]
                 [clojure-contrib "1.2.0"]
                 [ordered-set "0.1.0"]
                 [com.jcraft/jsch "0.1.42"]
                 [org.apache.ant/ant "1.8.1"]
                 [org.apache.ivy/ivy "2.2.0-rc1"]]
  :dev-dependencies [[org.clojars.ninjudd/lazytest "1.1.3-SNAPSHOT" :exclusions [swank-clojure]]])
