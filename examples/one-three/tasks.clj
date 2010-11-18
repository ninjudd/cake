(ns user
  (:use [clojure.contrib duck-streams]
        [clojure.contrib.java-utils :only (file)]
        cake cake.core cake.ant
        [cake.tasks.jar :only [build-uberjar uberjarfile]])
  (:import [org.apache.tools.ant.taskdefs Mkdir Copy]
           (java.io IOException)))

(defn latest-git-tag []
  (with-open [s (.getInputStream (.exec (Runtime/getRuntime) "git describe --tags"))]
    (first (read-lines s))))

(undeftask release)
(deftask release #{jar}
  (let [git-version (latest-git-tag)
        version-dir (str "resources/version")
        version-file (str "resources/version/version.txt")
        uberjar-name (str (:name *project*) "-" git-version ".jar")]
    (println "Version (from git):" (latest-git-tag))
    ;; catch IOException, we don't care if the directories exist already
    (try (ant Mkdir {:dir version-dir}) (catch IOException e))
    (spit version-file git-version)
    (binding [cake.tasks.jar/uberjarfile #(file uberjar-name)]
      (invoke uberjar))))
