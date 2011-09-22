(ns cake.project
  (:use cake
        [cake.file :only [file]]
        [clojure.string :only [trim-newline]]
        [clojure.java.shell :only [sh]]
        [useful.utils :only [adjoin]]
        [useful.map :only [update into-map map-vals]]
        [useful.fn :only [given]]
        [clojure.java.io :only [reader]]))

(defn group [dep]
  (if ('#{clojure clojure-contrib} dep)
    "org.clojure"
    (some #(% dep) [namespace name])))

(defn add-group [dep]
  (symbol (group dep) (name dep)))

(defn dep-map [deps]
  (let [[deps default-opts] (split-with (complement keyword?) deps)]
    (into {}
          (for [[dep version & opts] deps]
            [(add-group dep) (-> (adjoin (into-map default-opts) (into-map opts))
                                 (given version assoc :version version)
                                 (update :exclusions (partial map add-group)))]))))

(defmulti get-version identity)

(defmethod get-version :git [_]
  (:out (sh "git" "describe" "--tags" "--abbrev=0")))

(defmethod get-version :hg [_]
  (-> ".hgtags" reader line-seq last (.split " ") last))

(defmethod get-version :default [r]
  (println "No pre-defined get-version method for that key."))

(defn- assoc-path
  ([opts key default]
     (let [path (or (get opts key) default)]
       (assoc opts key (if (string? path)
                         [path]
                         (vec path)))))
  ([opts key base-key suffix]
     (assoc-path opts key (vec (map #(str (file % suffix))
                                    (get opts base-key))))))

(defn qualify [type deps]
  (map-vals deps #(assoc % type true)))

(defn create [project-name opts]
  (let [base-version (:version opts)
        version (trim-newline
                 (if (string? base-version)
                   base-version
                   (get-version base-version)))
        artifact (name project-name)
        artifact-version (str artifact "-" version)]
    (-> opts
        (assoc :artifact-id  artifact
               :group-id     (group project-name)
               :version      version
               :name         (or (:name opts) artifact)
               :aot          (or (:aot opts) (:namespaces opts))
               :context      (symbol (or (get *config* "project.context") (:context opts) "dev"))
               :jar-name     (or (:jar-name opts) artifact-version)
               :war-name     (or (:war-name opts) artifact-version)
               :uberjar-name (or (:uberjar-name opts) (str artifact-version "-standalone"))
               :dependencies (merge-with adjoin
                               (dep-map (mapcat opts [:dependencies :native-dependencies]))
                               (qualify :dev    (dep-map (:dev-dependencies  opts)))
                               (qualify :test   (dep-map (:test-dependencies opts)))
                               (qualify :plugin (dep-map (:cake-plugins      opts)))))
        (assoc-path :source-path        "src")
        (assoc-path :test-path          "test")
        (assoc-path :resources-path     "resources")
        (assoc-path :library-path       "lib")
        (assoc-path :dev-resources-path "dev")
        (assoc-path :compile-path       "classes")
        (assoc-path :test-compile-path  :test-path "classes")
        (given (:java-source-path opts) update :source-path conj (:java-source-path opts)))))
