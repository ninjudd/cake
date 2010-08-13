(ns cake.tasks.version
  (:use cake cake.ant)
  (:import [org.apache.tools.ant.taskdefs Replace]))

(def *version-levels* [:major :minor :patch])

(def defline (format "(defproject %s \"" (:artifact-id *project*) " \""))

(defn version-map [v]
  (let [[v snapshot] (.split v "-")]
    (into {:snapshot snapshot}
          (map vector
               *version-levels*
               (map #(Integer/parseInt %)
                    (.split v "\\."))))))

(defn bump [level & [snapshot]]
  (let [version (:version *project*)
        version-map (version-map version)
        version-map (apply assoc version-map
                           level (inc (level version-map))
                           (mapcat #(list % 0)
                                   (take-while (partial not= level)
                                               (reverse *version-levels*))))
        new-version (apply str (interpose "." (map version-map *version-levels*)))]
    (if snapshot
      (str new-version "-SNAPSHOT")
      new-version)))

(defn update-version [arg]
  (let [level (first (filter *opts* *version-levels*))
        level (if (not level) :patch level)
        new-version (if (= "bump" arg)
                      (bump level (:snapshot *opts*))
                      arg)]
    (ant Replace {:file "project.clj"
                  :token (str defline (:version *project*))
                  :value (str defline new-version)})
    new-version))

(deftask version
  "Display project version. Use 'bump [--major --minor --patch --snapshot]' to increment."
  (if-let [arg (first (:version *opts*))]
    (println (:artifact-id *project*)
             (:version *project*)
             "->"
             (:artifact-id *project*)
             (update-version arg))
    (println (:artifact-id *project*) (:version *project*))))
