(ns cake.project
  (:use cake
        [cake.file :only [file]]
        [clojure.string :only [trim-newline]]
        [clojure.java.shell :only [sh]]
        [useful.utils :only [adjoin]]
        [useful.map :only [update into-map map-vals]]
        [useful.fn :only [given to-fix !]]
        [clojure.java.io :only [reader]]))

(defn group [dep]
  (if ('#{clojure clojure-contrib} dep)
    "org.clojure"
    (some #(% dep) [namespace name])))

(defn add-group [dep]
  (symbol (group dep) (name dep)))

(defmulti get-version (to-fix (! keyword?) class))

(defmethod get-version String [version]
  version)

(defmethod get-version :git [_]
  (trim-newline (:out (sh "git" "describe" "--tags" "--abbrev=0"))))

(defmethod get-version :hg [_]
  (trim-newline (-> ".hgtags" reader line-seq last (.split " ") last)))

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

(defn dep-map [& deps]
  (let [[deps default-opts] (split-with (complement keyword?) (apply concat deps))]
    (into {}
          (for [[dep version & opts] deps]
            [(add-group dep) (-> (adjoin (into-map default-opts) (into-map opts))
                                 (given version assoc :version version)
                                 (update :exclusions (partial map add-group)))]))))

(defn create-project [project-name opts]
  (let [version  (get-version (:version opts))
        artifact (name project-name)
        pkg-name (str artifact "-" version)]
    (with-meta
      (-> opts
          (assoc :artifact-id  artifact
                 :group-id     (group project-name)
                 :version      version
                 :name         (or (:name opts) artifact)
                 :aot          (or (:aot opts) (:namespaces opts))
                 :context      (symbol (or (get *config* "project.context") (:context opts) "dev"))
                 :jar-name     (or (:jar-name opts) pkg-name)
                 :war-name     (or (:war-name opts) pkg-name)
                 :uberjar-name (or (:uberjar-name opts) (str pkg-name "-standalone"))
                 :dependencies (dep-map (mapcat opts [:dependencies :native-dependencies]))
                 :cake-plugins (dep-map (:cake-plugins opts)))
          (assoc-path :source-path        "src")
          (assoc-path :test-path          "test")
          (assoc-path :resources-path     "resources")
          (assoc-path :library-path       "lib")
          (assoc-path :dev-resources-path "dev")
          (assoc-path :compile-path       "classes")
          (assoc-path :test-compile-path  :test-path "classes")
          (given (:java-source-path opts)
                 update :source-path conj (:java-source-path opts)))
      {:context
       {'dev  {:dependencies (dep-map (:dev-dependencies opts))}
        'test {:dependencies (dep-map (mapcat opts [:dev-dependencies :test-dependencies]))}}})))

(defn create-context [opts]
  (-> opts
      (update :dependencies dep-map)))