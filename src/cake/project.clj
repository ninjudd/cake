(ns cake.project
  (:use [useful :only [assoc-or]])
  (:import [java.io File FileInputStream]
           [java.util Properties]))

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
               :version     version)
        (assoc-or :name artifact))))

(defn read-config []
  (let [file (File. ".cake/config")]
    (when (.exists file)
      (with-open [f (FileInputStream. file)]
        (into {} (doto (Properties.) (.load f)))))))

(def *config* (read-config))

(defn init [& files]
  (doseq [file files :when (.exists (java.io.File. file))]
    (load-file file)))
