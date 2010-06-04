(ns user
  (:use cake.ant
        [clojure.useful :only [abort]]
        [cake.tasks.jar :only [uberjarfile]])
  (:import [org.apache.tools.ant.taskdefs Copy ExecTask]))

(deftask check-gem-version
  (when (.endsWith (:version project) "SNAPSHOT")
    (abort "cannot build a gem for a snapshot version:" (:version project))))

(deftask gem => check-gem-version, uberjar
  (let [root (:root project)]
    (ant Copy {:file (uberjarfile project)  :tofile (str root "/gem/lib/cake.jar")})
    (ant Copy {:file (str root "/bin/cake") :tofile (str root "/gem/bin/cake")})
    (ant ExecTask {:executable "gem" :dir (str root "/gem")}
         (env {"CAKE_VERSION" (:version project)})
         (args ["build" "cake.gemspec"]))))

(deftask push => gem
  (let [gem (str "cake-" (:version project) ".gem")]
    (ant ExecTask {:executable "gem" :dir (str (:root project) "/gem")}
         (args ["push" gem]))))