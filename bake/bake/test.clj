(ns bake.test
  (:use [cake.contrib.find-namespaces :only [find-namespaces-in-dir]])
  (:require clojure.test
            [clojure.stacktrace :as stack]
            [com.georgejahad.difform :as diff]))

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

(defn test-type [test]
  (cond (namespace test) :fn
        (keyword?  test) :tag
        :else            :ns))

(defn group-opts [coll]
  ;; don't use group-by so we are compatible with 1.1
  (reduce
   (fn [ret x]
     (let [k (test-type x)]
       (assoc ret k (conj (get ret k []) x))))
   {}
   coll))

(defn get-grouped-tests [namespaces opts]
  (let [tests (:test opts)]
    (group-opts
     (if (nil? tests)
       namespaces
       (map prep-opt tests)))))

(defn timer [begin]
  (println "----")
  (println "Finished in" (/ (- (System/nanoTime) begin) (Math/pow 10 9)) "seconds.\n"))

(defn print-results [m]
  (println "\nRan" (:test m) "tests containing"
           (+ (:pass m) (:fail m) (:error m)) "assertions.")
  (println (:fail m) "failures," (:error m) "errors.")
  (when (:start-time m) (timer (:start-time m))))

(defmethod clojure.test/report :begin-test-ns [m]
  (clojure.test/with-test-out
   (println "\nTesting" (ns-name (:ns m)))))

(defmethod clojure.test/report :summary [m]
  (clojure.test/with-test-out
    (print-results m)))

(defmethod clojure.test/report :begin-auto [m])

(defmethod clojure.test/report :summary-auto [m]
  (clojure.test/with-test-out
    (if (and (= 0 (:fail m))
             (= 0 (:error m))
             (not (:full-report? m)))
      (println ".")
      (print-results m))))

(defn diff-actual [[f [_ expected actual]]]
  (diff/clean-difform expected actual))

(defmethod clojure.test/report :fail [m]
  (clojure.test/with-test-out
    (clojure.test/inc-report-counter :fail)
    (println "\nFAIL in" (clojure.test/testing-vars-str m))
    (when (seq clojure.test/*testing-contexts*) (println (clojure.test/testing-contexts-str)))
    (when-let [message (:message m)] (println message))
    (let [expected (:expected m)
          actual   (:actual m)]
      (println "expected:" (pr-str expected))
      (println "  actual:" (pr-str actual))
      (when (seq? actual)
        (diff-actual actual)))))

(declare start-time)

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
      (clojure.test/report (assoc @clojure.test/*report-counters* :type :summary :start-time start-time))
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
                 (clojure.test/report (assoc @clojure.test/*report-counters* :type :summary :start-time start-time))
                 @clojure.test/*report-counters*))))))

(defn run-tests-for-auto [grouped-tests & full-report]
  (for [ns (:ns grouped-tests)]
    (binding [clojure.test/*report-counters* (ref clojure.test/*initial-report-counters*)
              start-time (System/nanoTime)]
      (clojure.test/report {:type :begin-auto :ns ns})
      (clojure.test/test-all-vars ns)
      (clojure.test/report
       (let [report (assoc @clojure.test/*report-counters* :type :summary-auto :start-time start-time)]
         (if (first full-report)
           (assoc report :full-report? true)
           report)))
      @clojure.test/*report-counters*)))
