(ns bake.test
  (:use [bake.contrib.find-namespaces :only [find-namespaces-in-dir]])
  (:require clojure.test))

(defn map-tags [nses]
  (reduce (partial merge-with concat)
          (for [ns nses
                [name f] (ns-publics ns)
                tag (:tags (meta f))]
            {tag [f]})))

(defn all-test-namespaces [project]
  (find-namespaces-in-dir (java.io.File. "test")))

(defn prep-opt [str]
  (if (.startsWith str ":")
    (read-string str)
    (symbol str)))

(defn group-opts [coll]
  (group-by #(cond (and (namespace %)
                        (name %))     :fn
                   (keyword? %)       :tag
                   :else              :ns)
            coll))

(defn get-grouped-tests [namespaces opts]
  (let [tests (:test opts)]
    (group-opts
     (if (nil? tests)
       namespaces
       (map prep-opt tests)))))

(defonce auto-count (atom 0))

(defmethod clojure.test/report :begin-test-ns [m]
  (clojure.test/with-test-out
   (println "\nTesting" (ns-name (:ns m)))))

(defmethod clojure.test/report :summary [m]
  (clojure.test/with-test-out
   (println "\nRan" (:test m) "tests containing"
            (+ (:pass m) (:fail m) (:error m)) "assertions.")
   (println (:fail m) "failures," (:error m) "errors.")))

(defmethod clojure.test/report :begin-auto [m])

(defmethod clojure.test/report :summary-auto [m]
  (swap! auto-count inc)
  (clojure.test/with-test-out
    (if (and (= 0 (:fail m))
             (= 0 (:error m)))
      (println ".")
      (do
        (println "\nRan" (:test m) "tests containing"
                 (+ (:pass m) (:fail m) (:error m)) "assertions.")
        (println (:fail m) "failures," (:error m) "errors.")))))

(declare start-time)

(defn timer [begin]
  (println "----")
  (println "Finished in" (/ (- (System/nanoTime) begin) (Math/pow 10 9)) "seconds.\n"))

(defn run-tests-for-fns [grouped-tests]
  (when-let [input-fs (:fn grouped-tests)]
    (println "testing functions:" (apply str (interpose ", " input-fs)))
    (binding [clojure.test/*report-counters* (ref clojure.test/*initial-report-counters*)]
      (doseq [f input-fs] (clojure.test/test-var (ns-resolve (symbol (namespace f)) (symbol (name f)))))
      (list (assoc @clojure.test/*report-counters* :type :fns)))))

(defn run-tests-for-nses [grouped-tests]
  (for [ns (:ns grouped-tests)]
    (binding [clojure.test/*report-counters* (ref clojure.test/*initial-report-counters*)
              start-time (System/nanoTime)]
      (clojure.test/report {:type :begin-test-ns :ns ns})
      (clojure.test/test-all-vars ns)
      (clojure.test/report (assoc @clojure.test/*report-counters* :type :summary))
      (timer start-time)
      @clojure.test/*report-counters*)))

(defn run-tests-for-tags [grouped-tests test-namespaces]
  (when-let [input-tags (:tag grouped-tests)]
    (let [tags-to-fs (map-tags test-namespaces)]
      (doall (for [tag input-tags]
               (binding [clojure.test/*report-counters* (ref clojure.test/*initial-report-counters*)
                         start-time (System/nanoTime)]
                 (println "Testing" tag)
                 (doseq [test (tag tags-to-fs)]
                   (clojure.test/test-var test))
                 (clojure.test/report (assoc @clojure.test/*report-counters* :type :summary))
                 (timer start-time)
                 @clojure.test/*report-counters*))))))

(defn run-tests-for-auto [grouped-tests]
  (for [ns (:ns grouped-tests)]
    (binding [clojure.test/*report-counters* (ref clojure.test/*initial-report-counters*)
              start-time (System/nanoTime)]
      (clojure.test/report {:type :begin-auto :ns ns})
      (clojure.test/test-all-vars ns)
      (clojure.test/report (assoc @clojure.test/*report-counters* :type :summary-auto))
      @clojure.test/*report-counters*)))
