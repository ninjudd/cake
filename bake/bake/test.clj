(ns bake.test
  (:refer-clojure :exclude [group-by])
  (:use [cake.contrib.find-namespaces :only [find-namespaces-in-dir]]
        useful)
  (:require [clojure.test :as test]
            [clojure.stacktrace :as stack]))

(defn all-test-namespaces []
  (find-namespaces-in-dir (java.io.File. "test")))

(defn prep-opt [str]
  (if (.startsWith str ":")
    (read-string str)
    (symbol str)))

(defn test-type [test]
  (cond (namespace test) :fn
        (keyword?  test) :tag
        :else            :ns))

;from 1.2
(defn- group-by [f coll]
  (persistent!
   (reduce
    (fn [ret x]
      (let [k (f x)]
        (assoc! ret k (conj (get ret k []) x))))
    (transient {}) coll)))

(defn get-grouped-tests [namespaces opts]
  (let [tests (:test opts)]
    (group-by
     test-type
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

(defmethod test/report :begin-test-ns [m]
  (test/with-test-out
   (println "\nTesting" (ns-name (:ns m)))))

(defmethod test/report :summary [m]
  (test/with-test-out
    (print-results m)))

(defmethod test/report :begin-auto [m])

(defmethod test/report :summary-auto [m]
  (test/with-test-out
    (if (and (= 0 (:fail m))
             (= 0 (:error m))
             (not (:full-report? m)))
      (println ".")
      (print-results m))))

(comment defn diff-actual [[f [_ expected actual]]]
  (diff/clean-difform expected actual))

(defmethod test/report :fail [m]
  (test/with-test-out
    (test/inc-report-counter :fail)
    (println "\nFAIL in" (test/testing-vars-str m))
    (when (seq test/*testing-contexts*) (println (test/testing-contexts-str)))
    (when-let [message (:message m)] (println message))
    (let [expected (:expected m)
          actual   (:actual m)]
      (println "expected:" (pr-str expected))
      (println "  actual:" (pr-str actual))
      (comment when (seq? actual)
        (diff-actual actual)))))

(declare start-time)

(defn test-fn-seq [pairs]
  (binding [test/*report-counters* (ref test/*initial-report-counters*)
            start-time (System/nanoTime)]
    (doseq [[ns fs] pairs]
      (let [ns (find-ns (symbol ns))
            once-fixture-fn (test/join-fixtures (::test/once-fixtures (meta ns)))
            each-fixture-fn (test/join-fixtures (::test/each-fixtures (meta ns)))]
        (once-fixture-fn
         (fn [] (doseq [f fs]
                  (each-fixture-fn (fn [] (test/test-var f))))))))
    (test/report (assoc @test/*report-counters* :type :summary :start-time start-time))))

(defn run-tests-for-fns [grouped-tests]
  (when-let [input-fs (seq (for [[ns fs] (group-by namespace (:fn grouped-tests))
                                 f fs]
                             [ns (ns-resolve (symbol (namespace f))
                                             (symbol (name f)))]))]
    (test-fn-seq (reduce (fn [m [k v]] (update m k conj v)) {} input-fs))))

(defn run-tests-for-nses [grouped-tests]
  (for [ns (:ns grouped-tests)]
    (binding [test/*report-counters* (ref test/*initial-report-counters*)
              start-time (System/nanoTime)]
      (test/report {:type :begin-test-ns :ns ns})
      (test/test-all-vars (find-ns ns))
      (test/report (assoc @test/*report-counters* :type :summary :start-time start-time))
      @test/*report-counters*)))

(defn map-tags [nses tags]
  (reduce (partial merge-with concat)
          (for [ns nses
                [name f] (ns-publics ns)
                tag (:tags (meta f))]
            (when (contains? (set tags) tag)
              {ns [f]}))))

(defn run-tests-for-tags [grouped-tests test-namespaces]
  (when-let [input-tags (:tag grouped-tests)]
    (let [input-fs (-> test-namespaces (map-tags input-tags) )]
      (test-fn-seq input-fs))))

(defn run-tests-for-auto [grouped-tests & full-report]
  (for [ns (:ns grouped-tests)]
    (binding [test/*report-counters* (ref test/*initial-report-counters*)
              start-time (System/nanoTime)]
      (test/report {:type :begin-auto :ns ns})
      (test/test-all-vars ns)
      (test/report
       (let [report (assoc @test/*report-counters* :type :summary-auto :start-time start-time)]
         (if (first full-report)
           (assoc report :full-report? true)
           report)))
      @test/*report-counters*)))
