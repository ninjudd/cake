(ns cake.tasks.clean
  (:use cake cake.ant)
  (:import (org.apache.tools.ant.taskdefs Delete Mkdir)))

(deftask clean
  "Remove cake build artifacts."
  (when *project*
    (bake [] (System/exit 0)))
  (let [files ["pom.xml" "classes" "lib" "build"]]
    (doseq [file (map file (concat files (:clean *project*)))]
      (if (.isDirectory file)
        (ant Delete {:dir file})
        (ant Delete {:file file})))))
