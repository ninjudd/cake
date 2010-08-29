(ns cake.tasks.clean
  (:use cake cake.core cake.ant)
  (:import (org.apache.tools.ant.taskdefs Delete Mkdir)))

(defn clean-dir [dir]
  (when (seq (rest (file-seq dir)))
    (log "Deleting" (str (.getPath dir) "/*"))
    (ant Delete {:include-empty-dirs true}
      (add-fileset {:dir dir :includes "**/*"}))))

(deftask clean
  "Remove cake build artifacts."
  (ant Delete {:verbose true}
    (add-fileset {:dir (file) :includes "*.jar"})
    (add-fileset {:dir (file) :includes "*.war"}))
  (doseq [dir ["classes" "build"]]
    (clean-dir (file dir)))
  (when (= ["deps"] (:clean *opts*))
    (clean-dir (file "lib"))
    (ant Delete {:verbose}
      (add-fileset {:dir (file) :includes "pom.xml"}))))
