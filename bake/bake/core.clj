(ns bake.core
  (:use cake
        [clojure.string :only [join]]))

(prn (ns-publics 'cake))

(defn merge-in
  "Merge two nested maps."
  [left right]
  (if (map? left)
    (merge-with merge-in left right)
    right))

(defn log [& message]
  (println (format "%11s %s" (str "[" *current-task* "]") (join " " message))))

(defn debug? []
  (boolean (or (:d *opts*) (:debug *opts*))))

(defn verbose? []
  (boolean (or (:v *opts*) (:verbose *opts*))))

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