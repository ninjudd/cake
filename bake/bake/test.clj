(ns bake.test
  (:import [java.io File])
  (:use [clojure.contrib.find-namespaces :only [find-namespaces-in-dir]]))

(defn map-tags [nses]
  (reduce #((for [[k v] %2] (for [tag (:tags (meta v))] (assoc %1 tag v))))
          {}
          (for [ns nses] (ns-publics ns))))

(defn all-test-namespaces [project] (find-namespaces-in-dir (File. (:root project) "test")))

(defn prep-opt [str]
  (if (.startsWith str ":")
    (read-string str)
    (symbol str)))

(defn group-opts [coll]
  (group-by #(cond (and (namespace %) (name %)) :fn
                   (keyword? %)                 :tag
                   :else                        :ns)
            coll))

(defn grouped-tests [project opts]
  (let [tests (:test opts)
        foo (println "tests:" tests)]
    (group-opts
     (if (nil? tests)
       (all-test-namespaces project)
       (map prep-opt tests)))))
