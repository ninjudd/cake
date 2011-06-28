(ns tasks
  (:use cake cake.core cake.file uncle.core
        [cake.utils :only [git]]
        [bake.core :only [log]]
        [cake.tasks.jar :only [build-uberjar jars jarfile]]
        [cake.tasks.version :only [snapshot? snapshot-timestamp]])
  (:import [org.apache.tools.ant.taskdefs Jar Copy Move ExecTask]
           [java.io File]))

(defn bakejar []
  (file (str "bake-" (:version *project*) ".jar")))

(defn clojure-jar []
  (str "clojure-" (.replace (clojure-version) "SNAPSHOT" "*") ".jar"))

(defn add-dev-jars [task]
  (doseq [jar (fileset-seq {:dir "lib/dev" :includes "*.jar"})]
    (add-zipfileset task {:src jar :includes "**/*.clj" :excludes "META-INF/**/project.clj"})))

(undeftask uberjar)
(deftask uberjar #{jar}
  "Create a standalone jar containing all project dependencies."
  (let [jar (jarfile)]
    (build-uberjar jar (jars :excludes [(clojure-jar)]))
    (ant Jar {:dest-file (bakejar)}
      (add-fileset {:dir "dev"})
      add-dev-jars)
    (ant Jar {:dest-file (jarfile) :update true}
      add-dev-jars)))

(undeftask release)
(deftask release #{uberjar tag}
  "Release project jar to github"
  (when-not (snapshot? (:version *project*))
    (with-root (file "releases")
      (git "pull"))
    (ant Copy {:file (jarfile) :todir (file "releases" "jars")})
    (ant Copy {:file (bakejar) :todir (file "releases" "jars")})
    (ant Copy {:file (file "bin" "cake") :tofile (file "releases" "cake")})
    (spit (file "releases/stable") (:version *project*))
    (with-root (file "releases")
      (git "add" "jars" "cake" "stable")
      (git "commit" "--allow-empty" "-m" (format "'release cake %s'" (:version *project*)))
      (git "push"))))
