(ns user
  (:use cake cake.ant
        [clojure.useful :only [abort]]
        [cake.tasks.jar :only [release uberjarfile]])
  (:import [org.apache.tools.ant.taskdefs Jar Copy Move ExecTask]
           [java.io File]))

(defn bakejar [project]
  (format "bake-%s.jar" (:version project)))

(deftask uberjar
  (let [jarfile (uberjarfile project)
        bakejar (bakejar project)]
    (ant Jar {:dest-file bakejar}
         (add-fileset {:dir "bake"}))
    (ant Jar {:dest-file jarfile :update true}
         (add-fileset {:dir (:root project) :includes bakejar}))))

(defn snapshot? [project]
  (.endsWith (:version project) "SNAPSHOT"))

(deftask gem
  (if (snapshot? project)
    (println "will not make gem since this is a snapshot version:" (:version project))
    (let [root (:root project)]
      (run-task 'uberjar)
      (ant Copy {:file (uberjarfile project) :tofile (str root "/gem/lib/cake.jar")})
      (ant Copy {:file (bakejar project) :tofile (str root "/gem/lib/bake.jar")})
      (let [cake-bin (str root "/gem/bin/cake")]
        (ant Copy {:file (str root "/bin/cake") :tofile cake-bin}))
      (ant ExecTask {:executable "gem" :dir (str root "/gem")}
           (env {"CAKE_VERSION" (:version project)})
           (args ["build" "cake.gemspec"])))))

(remove-task! 'release)
(deftask release => uberjar, gem
  (when-not (snapshot? project)
    (let [gem (str "cake-" (:version project) ".gem")]
      (log "Releasing gem: " gem)
      (ant ExecTask {:executable "gem" :dir (str (:root project) "/gem")}
           (args ["push" gem]))))
  (let [uberjarfile (uberjarfile project)
        jarfile     (File. (:root project) "cake.jar")]
    (ant Copy {:file uberjarfile :tofile jarfile})
    (release jarfile)))