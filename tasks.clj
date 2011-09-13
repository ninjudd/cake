(ns tasks
  (:use cake cake.core cake.file uncle.core
        [cake.utils :only [git]]
        [bake.core :only [log]]
        [cake.tasks.jar :only [build-uberjar jars uberjarfile]]
        [cake.tasks.version :only [snapshot? stable?]])
  (:import [org.apache.tools.ant.taskdefs Jar Copy Move ExecTask]
           [java.io File]))

(defn bakejar []
  (file (str "bake-" (:version *project*) ".jar")))

(defn clojure-jar? [file]
  (re-matches #"^.*/clojure-[\d\.]+[-\w*]?\.jar$" file))

(defn add-dev-jars [task]
  (doseq [jar (fileset-seq {:dir "lib/dev" :includes "*.jar"})]
    (add-zipfileset task {:src jar :includes "**/*.clj" :excludes "META-INF/**/project.clj"})))

(undeftask uberjar)
(deftask uberjar #{jar}
  "Create a standalone jar containing all project dependencies."
  (let [jar (uberjarfile)]
    (build-uberjar jar (remove clojure-jar? (jars)))
    (ant Jar {:dest-file (bakejar)}
      (add-fileset {:dir "dev"})
      add-dev-jars)
    (ant Jar {:dest-file jar :update true}
      add-dev-jars)))

(undeftask release)
(deftask release #{uberjar tag}
  "Release project jar to github"
  (let [version (:version *project*)]
    (when-not (snapshot? version)
      (with-root (file "releases")
        (git "pull"))
      (ant Copy {:file (uberjarfile) :tofile (file "releases" "jars" (format "cake-%s.jar" version))})
      (ant Copy {:file (bakejar)     :todir  (file "releases" "jars")})
      (when (stable? version)
        (ant Copy {:file (file "bin" "cake") :tofile (file "releases" "cake")}))
      (with-root (file "releases")
        (git "add" "jars" "cake")
        (git "commit" "--allow-empty" "-m" (format "'release cake %s'" (:version *project*)))
        (git "push")))))
