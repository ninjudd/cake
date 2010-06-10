(ns user
  (:use cake cake.ant
        [clojure.useful :only [abort]]
        [cake.tasks.jar :only [release uberjarfile]])
  (:import [org.apache.tools.ant.taskdefs Jar Copy Move ExecTask]
           [java.io File]))

(defn bakejar [project]
  (file (format "bake-%s.jar" (:version project))))

(deftask uberjar
  (let [jarfile (uberjarfile project)
        bakejar (bakejar project)]
    (ant Jar {:dest-file bakejar}
         (add-fileset {:dir "bake"}))
    (ant Jar {:dest-file jarfile :update true}
         (add-fileset {:file bakejar}))))

(defn snapshot? [project]
  (.endsWith (:version project) "SNAPSHOT"))

(deftask gem
  "Build standalone gem package."
  (if (snapshot? project)
    (println "will not make gem since this is a snapshot version:" (:version project))
    (do (run-task 'uberjar)
        (ant Copy {:file (uberjarfile project) :tofile (file "gem/lib/cake.jar")})
        (ant Copy {:file (bakejar project)     :tofile (file "gem/lib/bake.jar")})
        (ant Copy {:file (file "bin/cake") :tofile (file "gem/bin/cake")})
        (ant ExecTask {:executable "gem" :dir (file "gem")}
             (env {"CAKE_VERSION" (:version project)})
             (args ["build" "cake.gemspec"])))))

(undeftask release)
(deftask release => uberjar, gem
  "Release project jar to clojars and gem package to rubygems."
  (when-not (snapshot? project)
    (let [gem (str "cake-" (:version project) ".gem")]
      (log "Releasing gem: " gem)
      (ant ExecTask {:executable "gem" :dir (file "gem")}
           (args ["push" gem]))))
  (let [uberjarfile (uberjarfile project)
        jarfile     (file "cake.jar")]
    (ant Copy {:file uberjarfile :tofile jarfile})
    (release jarfile)))