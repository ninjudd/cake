(ns cake.tasks.deps
  (:use [cake :only [*opts* *project*]]
        [bake.core :only [log]]
        [cake.file :only [older?]]
        [cake.core :only [deftask defile]]
        [cake.tasks.jar :only [jarfile]]
        [cake.deps :only [fetch-deps! print-deps deps-cache]]
        [cake.classloader :only [reset-classloaders!]]
        [depot.deps :only [publish]]
        [depot.pom :only [xml-tags]]))

(defile "pom.xml" #{"project.clj"}
  (xml-tags :project *project*))

(deftask deps
  "Fetch dependencies specified in project.clj."
  {called-directly? :deps quiet? :q}
  (when (fetch-deps! :force called-directly?)
    (reset-classloaders!))
  (when (and called-directly? (not quiet?))
    (print-deps)))

(deftask update #{deps}
  "Update cake plugins and restart the persistent JVM."
  (System/exit 0))

(deftask pom #{"pom.xml"})

(deftask install #{jar pom}
  "Install jar to local repository."
  (let [jar (jarfile)]
    (log "Copying" jar "to local repository")
    (publish *project* jar "pom.xml")))