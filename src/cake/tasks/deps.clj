(ns cake.tasks.deps
  (:use [cake :only [*opts* *project*]]
        [bake.core :only [log]]
        [cake.core :only [deftask defile]]
        [cake.tasks.jar :only [jarfile]]
        [cake.deps :only [fetch-deps! *overwrite* print-deps]]
        [cake.project :only [reset-classloaders!]]
        [depot.deps :only [publish]]
        [depot.pom :only [prxml-tags]]))

(defile "pom.xml" #{"project.clj"}
  (prxml-tags :project *project*))

(deftask deps
  "Fetch dependencies specified in project.clj."
  (binding [*overwrite* true]
    (fetch-deps!))
  (print-deps)
  (reset-classloaders!))

(deftask update #{deps}
  "Update cake plugins and restart the persistent JVM."
  (System/exit 0))

(deftask pom #{"pom.xml"})

(deftask install #{jar pom}
  "Install jar to local repository."
  (let [jar (jarfile)]
    (log "Copying" jar "to local repository")
    (publish *project* jar "pom.xml")))