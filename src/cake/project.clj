(ns cake.project
  (:use cake
        [clojure.string :only [join]]
        [cake.utils.useful :only [assoc-or update merge-in]])
  (:import [java.io File]))

(defn log [& message]
  (println (format "%11s %s" (str "[" *current-task* "]") (join " " message))))

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
  (let [artifact (name project-name)
        artifact-version (str artifact "-" version)]
    (-> opts
        (assoc :artifact-id  artifact
               :group-id     (group project-name)
               :aot          (or (:aot opts) (:namespaces opts))
               :version      version
               :context      (symbol (or (:context opts) "dev"))
               :jar-name     (or (:jar-name opts)artifact-version)
               :uberjar-name (or (:uberjar-name opts) (str artifact-version "-standalone"))
               :uberwar-name (or (:uberwar-name opts) artifact-version))
        (update :dependencies     dep-map)
        (update :dev-dependencies dep-map)
        (assoc-or :name artifact))))

(def global-root (.getPath (File. (System/getProperty "user.home") ".cake")))

(defn files [local-files global-files]
  (into (map #(File. %) local-files)
        (map #(File. global-root %) global-files)))

(def classpath
  (for [url (.getURLs (java.lang.ClassLoader/getSystemClassLoader))]
    (File. (.getFile url))))

(defn load-files [files]
  (doseq [file files :when (.exists file)]
    (load-file (.getPath file))))

(defn current-context []
  (if-let [context (get-in *opts* [:context 0])]
    (symbol context)))

(defn project-with-context [context]
  (merge-in project-root
            (assoc (context *context*)
              :context context)))

(defmacro with-context [context & forms]
  `(let [context# (symbol (name (or ~context (:context *project*))))]
     (binding [*project* (project-with-context context#)]
       ~@forms)))

(defmacro with-context! [context & forms]
  `(let [context# (symbol (name (or ~context (:context *project*))))]
     (alter-var-root #'*project* (fn [_#] (project-with-context context#)))
     (do ~@forms)
     (alter-var-root #'*project* project-root)))
