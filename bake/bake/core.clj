(ns bake.core
  (:use cake
        [clojure.string :only [join]]))

(defn merge-in
  "Merge two nested maps."
  [left right]
  (if (map? left)
    (merge-with merge-in left right)
    right))

(defn log [& message]
  (println (format "%11s %s" (str "[" *current-task* "]") (join " " message))))

(defn in-cake-jvm?
  "Returns true if we are running from a jvm started by cake."
  []
  (not (nil? (System/getProperty "cake.project"))))

(defn in-project-classloader?
  "Returns true if this code is running in the project classloader."
  []
  (= java.net.URLClassLoader
     (class (.getClassLoader clojure.lang.RT))))

(defn debug? []
  (boolean (or (:d *opts*) (:debug *opts*))))

(defn verbose? []
  (boolean (or (:v *opts*) (:verbose *opts*))))

(defn current-context []
  (if-let [context (get-in *opts* [:context 0])]
    (symbol context)))

(defn project-with-context [context]
  (if (nil? context)
    *project-root*
    (merge-in *project-root*
              (assoc (context *context*)
                :context context))))

(defmacro with-context [context & forms]
  `(let [context# (symbol (name (or ~context (:context *project*))))]
     (binding [*project* (project-with-context context#)]
       ~@forms)))

(defn set-project! [project]
  (alter-var-root #'*project* (fn [_] project)))

(defn set-context! [context]
  (let [context (or context (:context *project*))]
    (set-project! (project-with-context context))))

(defmacro with-context! [context & forms]
  `(try (set-context! ~context)
        (do ~@forms)
        (set-project! *project-root*)))
