(ns cake.project
  (:use cake
        [useful :only [assoc-or update]])
  (:import [java.io File]))

(defn group [project]
  (if (or (= project 'clojure) (= project 'clojure-contrib))
    "org.clojure"
    (or (namespace project) (name project))))

(defn dep-map [deps]
  (into {}
    (for [[dep version & opts] deps]
      [dep (apply hash-map :version version opts)])))

(defn create [project-name version opts]
  (let [artifact (name project-name)]
    (-> opts
        (assoc :artifact-id  artifact
               :group-id     (group project-name)
               :aot          (or (:aot opts) (:namespaces opts))
               :version      version)
        (update :dependencies     dep-map)
        (update :dev-dependencies dep-map)
        (assoc-or :name artifact))))

(defn init [file]
  (when (.exists (File. file))
    (load-file file)))