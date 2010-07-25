(ns cake.project
  (:use [useful :only [assoc-or]])
  (:require [cake.swank :as swank])
  (:import [java.io File]))

(defn group [project]
  (if (or (= project 'clojure) (= project 'clojure-contrib))
    "org.clojure"
    (or (namespace project) (name project))))

(defn create-project [project-name version opts]
  (let [root (.getParent (File. *file*))
        artifact (name project-name)]
    (-> opts
        (assoc :artifact-id artifact
               :group-id    (group project-name)
               :root        root
               :version     version
               :swank       (swank/config))
        (assoc-or :name artifact))))

(defn init [& files]
  (doseq [file files :when (.exists (java.io.File. file))]
    (load-file file)))
