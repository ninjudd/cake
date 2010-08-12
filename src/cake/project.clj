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
               :aot         (or (:aot opts) (:namespaces opts))
               :version     version)
        (assoc-or :name artifact))))

(defn read-config [file]
  (if (.exists file)
    (with-open [f (FileInputStream. file)]
      (into {} (doto (Properties.) (.load f))))
    {}))

(def *config* (merge (read-config (File. (System/getProperty "user.home") ".cake/config"))
                     (read-config (File. ".cake/config"))))

(defn init [& files]
  (doseq [file files :when (.exists (java.io.File. file))]
    (load-file file)))
