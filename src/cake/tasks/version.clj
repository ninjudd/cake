(ns cake.tasks.version
  (:use cake cake.core cake.utils.version
        [cake.utils :only [git]]
        [bake.core :only [log]]
        [uncle.core :only [ant]]
        [useful.map :only [update]])
  (:import [org.apache.tools.ant.taskdefs Replace]))

(defn defline [version]
  (format "(defproject %s \"%s\"" (:artifact-id *project*) version))

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
                  :token (defline (:version *project*))
                  :value (defline new-version)})
    new-version))

(deftask version
  "Display project version. Use 'bump --(major|minor|patch|snapshot)' to increment."
  (if-let [action (first (:version *opts*))]
    (println (:artifact-id *project*)
             (:version *project*)
             "->"
             (:artifact-id *project*)
             (update-version action))
    (println (:artifact-id *project*) (:version *project*))))

(defn snapshot? [version]
  (.endsWith version "-SNAPSHOT"))

(defn stable? [version]
  (not (.contains version "-")))

(defn snapshot-timestamp [version]
  (if (snapshot? version)
    (let [time  (System/currentTimeMillis)
          ftime #(format (apply str (map (partial str "%1$t") %)) time)]
      (.replaceAll version "SNAPSHOT" (str (ftime "Ymd") "." (ftime "HMS"))))
    version))

(deftask tag
  "Create a git tag for the current version."
  (let [version (snapshot-timestamp (:version *project*))]
    (git "tag" "-fa" version "-m" (format "'version %s'" version))
    (log "created git tag" version)))
