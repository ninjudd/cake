(ns user
  (:use bake bake.ant
        [useful :only [abort]]
        [bake.tasks.jar :only [release-to-clojars uberjarfile]])
  (:import [org.apache.tools.ant.taskdefs Jar Copy Move ExecTask]
           [java.io File]))

(defn cakejar [project]
  (file (format "cake-%s.jar" (:version project))))

(defn add-dev-jars [task]
  (doseq [jar (fileset-seq {:dir "lib/dev" :includes "*.jar"})]
    (add-zipfileset task {:src jar :includes "**/*.clj" :excludes "META-INF/**/project.clj"})))

(deftask uberjar
  (let [jarfile (uberjarfile project)
        cakejar (cakejar project)]
    (ant Jar {:dest-file cakejar}
         (add-fileset {:dir "cake"})
         (add-dev-jars))
    (ant Jar {:dest-file jarfile :update true}
         (add-fileset {:file cakejar})
         (add-dev-jars))))

(defn snapshot? [project]
  (.endsWith (:version project) "SNAPSHOT"))

(deftask gem
  "Build standalone gem package."
  (if (snapshot? project)
    (println "will not make gem since this is a snapshot version:" (:version project))
    (do (run-task 'uberjar)
        (ant Copy {:file (uberjarfile project) :tofile (file "gem/lib/bake.jar")})
        (ant Copy {:file (cakejar project)     :tofile (file "gem/lib/cake.jar")})
        (ant Copy {:file (file "bin/bake") :tofile (file "gem/bin/bake")})
        (ant ExecTask {:executable "gem" :dir (file "gem")}
             (env {"BAKE_VERSION" (:version project)})
             (args ["build" "bake.clj.gemspec"])))))

(undeftask release)
(deftask release #{uberjar gem}
  "Release project jar to clojars and gem package to rubygems."
  (when-not (snapshot? project)
    (let [gem (str "bake-" (:version project) ".gem")]
      (log "Releasing gem: " gem)
      (ant ExecTask {:executable "gem" :dir (file "gem")}
           (args ["push" gem]))))
  (let [uberjarfile (uberjarfile project)
        jarfile     (file "bake.jar")]
    (ant Copy {:file uberjarfile :tofile jarfile})
    (release-to-clojars jarfile)))