(ns cake.project
  (:use cake
        [useful :only [assoc-or]])
  (:import [java.io File]))

(defn group [project]
  (if (or (= project 'clojure) (= project 'clojure-contrib))
    "org.clojure"
    (or (namespace project) (name project))))

(defn create [project-name version opts]
  (let [root (.getParent (File. *file*))
        artifact (name project-name)]
    (-> opts
        (assoc :artifact-id artifact
               :group-id    (group project-name)
               :root        root
               :aot         (or (:aot opts) (:namespaces opts))
               :version     version)
        (assoc-or :name artifact))))

(defn init [& files]
  (doseq [file files :when (.exists (File. file))]
    (load-file file)))
