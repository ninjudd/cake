(ns cake.tasks.version
  (:use cake cake.core cake.ant
        [bake.core :only [log]]
        [cake.utils.useful :only [update]]
        [clojure.string :only [join]]
	[cake.utils :only [git]])
  (:import [org.apache.tools.ant.taskdefs Replace]))

(def version-levels [:major :minor :patch])
(def defline (str "(defproject " (:artifact-id *project*) " \"%s\""))

(defn version-map [version]
  (let [[version snapshot] (.split version "-")]
    (into {:snapshot snapshot}
          (map vector
               version-levels
               (map #(Integer/parseInt %)
                    (.split version "\\."))))))

(defn version-str [version]
  (str (join "." (map #(or (version %) 0) version-levels))
       (when (version :snapshot) "-SNAPSHOT")))

(defn bump
  ([] (bump (version-map (:version *project*))
            (first (filter *opts* version-levels))
            (:snapshot *opts*)))
  ([version level snapshot?]
      (let [snapshot (when-not snapshot? :snapshot)
            level    (or level (when-not (:snapshot version) :patch))
            version  (if snapshot? (assoc version :snapshot true) version)]
        (if level
          (reduce dissoc (update version level inc)
                  (conj (take-while (partial not= level) (reverse version-levels)) snapshot))
          (dissoc version snapshot)))))

(defn update-version [action]
  (let [new-version (if (= "bump" action) (version-str (bump)) action)]
    (ant Replace {:file "project.clj"
                  :token (format defline (:version *project*))
                  :value (format defline new-version)})
    new-version))

(deftask version
  "Display project version. Use 'bump [--major --minor --patch --snapshot]' to increment."
  (if-let [action (first (:version *opts*))]
    (println (:artifact-id *project*)
             (:version *project*)
             "->"
             (:artifact-id *project*)
             (update-version action))
    (println (:artifact-id *project*) (:version *project*))))

(deftask tag
  "Create a git tag for the current version."
  (let [version (:version *project*)]
    (if (.endsWith version "SNAPSHOT")
      (println "refusing to create tag for snapshot version:" version)
      (do (git "tag" "-fa" version "-m" (format "'version %s'" version))
          (log "created git tag" version)))))