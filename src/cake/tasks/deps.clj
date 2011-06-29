(ns cake.tasks.deps
  (:use [cake :only [*opts* *project*]]
        [cake.deps :only [clear-deps! fetch-deps! *overwrite* print-deps]]
        [cake.core :only [deftask defile]]
        [depot.pom :only [prxml-tags]]))

(defile "pom.xml" #{"project.clj"}
  (prxml-tags :project *project*))

(deftask pom #{"pom.xml"})

(deftask deps #{"pom.xml"}
  "Fetch dependencies specified in project.clj."
  (when (= "force" (first (:deps *opts*)))
    (clear-deps!))
  (binding [*overwrite* true]
    (fetch-deps!))
  (print-deps))

(deftask update #{deps}
  "Update cake plugins and restart the persistent JVM."
  (System/exit 0))