(ns cake.tasks.dependencies
  (:use cake cake.leiningen))

(deftask deps "Fetch dependencies and create pom.xml"
  (lein deps project)
  (lein pom project))
