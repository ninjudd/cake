(ns cake.project
  (:use cake
        [cake.utils.useful :only [assoc-or update merge-in]])
  (:import [java.io File]))

(defn debug? []
  (boolean (or (:d *opts*) (:debug *opts*))))

(defn verbose? []
  (boolean (or (:v *opts*) (:verbose *opts*))))

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
               :version      version
               :context      (symbol (or (:context opts) "dev")))
        (update :dependencies     dep-map)
        (update :dev-dependencies dep-map)
        (assoc-or :name artifact))))

(defn load-files []
  (doseq [f ["project.clj" "context.clj" "tasks.clj" "dev.clj"]
          :when (.exists (File. f))]
    (load-file f))
  (let [global-project (File. (System/getProperty "user.home") ".cake")]
    (when-not (= (.getPath global-project) (System/getProperty "cake.project"))
      (doseq [f ["tasks.clj" "dev.clj"]
              :let [file (File. global-project f)]
              :when (.exists file)]
        (load-file (.getPath file))))))

(defn current-context []
  (if-let [context (get-in *opts* [:context 0])]
    (symbol context)))

(defmacro with-context [context & forms]
  `(let [context# (or ~context (:context *project*))]
     (binding [*project* (merge-in (.getRoot #'*project*)
                                   (assoc (context# *context*)
                                     :context context#))]
       ~@forms)))
