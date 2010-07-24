(ns bake.tasks.clean
  (:use bake bake.ant)
  (:import (org.apache.tools.ant.taskdefs Delete Mkdir)))

(deftask clean
  "Remove bake build artifacts."
  (when bake/cake-port
    (bake [] (System/exit 0)))
  (let [files ["pom.xml" "classes" "lib" "build"]]
    (doseq [file (map file (concat files (:clean project)))]
      (if (.isDirectory file)
        (ant Delete {:dir file})
        (ant Delete {:file file})))))
