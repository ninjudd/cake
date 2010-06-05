(ns bake.test
  (:import [java.io File])
  (:use [clojure.contrib.find-namespaces :only [find-namespaces-in-dir]]))

(defn map-tags [nses] 
  (reduce (partial merge-with concat)
          (for [ns nses
                [name f] (ns-publics ns)
                tag (:tags (meta f))]
            {tag [f]})))

(def all-test-namespaces
     (memoize (fn [project]
                (find-namespaces-in-dir (File. (:test-path project))))))

(defn prep-opt [str]
  (if (.startsWith str ":")
    (read-string str)
    (symbol str)))

(defn group-opts [coll]
  (group-by #(cond (and (namespace %) (name %)) :fn
                   (keyword? %) :tag
                   :else :ns)
            coll))

(defn get-grouped-tests [project opts]
  (let [tests (:test opts)]
    (group-opts
     (if (nil? tests)
       (all-test-namespaces project)
       (map prep-opt tests)))))

(defn run-tests-for-fns [grouped-tests]
  (when-let [input-fs (:fn grouped-tests)]
    (println "testing functions:" (apply str (interpose ", " input-fs)))
    (binding [clojure.test/*report-counters* (ref clojure.test/*initial-report-counters*)]
      (doseq [f input-fs] (clojure.test/test-var (ns-resolve (symbol (namespace f)) (symbol (name f)))))
      (list (assoc @clojure.test/*report-counters* :type :fns)))))

(defn run-tests-for-nses [grouped-tests]
  (for [ns (:ns grouped-tests)]
    (binding [clojure.test/*report-counters* (ref clojure.test/*initial-report-counters*)]
      (clojure.test/report {:type :begin-test-ns :ns ns})
      (clojure.test/test-all-vars ns)
      (clojure.test/report (assoc @clojure.test/*report-counters* :type :summary))
      (println "----")
      @clojure.test/*report-counters*)))

(defn run-tests-for-tags [grouped-tests test-namespaces]
  (when-let [input-tags (:tag grouped-tests)]
    (let [tags-to-fs (map-tags test-namespaces)]
      (doall (for [tag input-tags]
               (binding [clojure.test/*report-counters* (ref clojure.test/*initial-report-counters*)]
                 (println "Testing" tag)
                 (doseq [test (tag tags-to-fs)]
                   (clojure.test/test-var test))
                 (clojure.test/report (assoc @clojure.test/*report-counters* :type :summary))
                 (println "----" )
                 @clojure.test/*report-counters*))))))

