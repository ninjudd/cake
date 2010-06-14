(ns cake.tasks.clean
  (:use cake cake.ant)
  (:import (org.apache.tools.ant.taskdefs Delete Mkdir)))

(deftask clean
  (bake [] (System/exit 0))
  (let [files ["pom.xml" "classes" "lib"]]
    (doseq [file (map file (concat files (:clean project)))]
      (if (.isDirectory file)
        (do (ant Delete {:dir file})
            (ant Mkdir {:dir file}))
        (ant Delete {:file file})))))

