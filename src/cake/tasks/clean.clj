(ns cake.tasks.clean
  (:use cake cake.core cake.ant)
  (:import (org.apache.tools.ant.taskdefs Delete Mkdir)))

(defn clean-dir [dir]
  (ant Delete {:include-empty-dirs true}
       (add-fileset {:dir dir :includes "**/*"})))

(deftask clean
  "Remove cake build artifacts."
  (doseq [dir ["classes" "build"]]
    (clean-dir dir))
  (when (= ["deps"] (:clean *opts*))
    (clean-dir "lib")
    (ant Delete {:file "pom.xml"})))
