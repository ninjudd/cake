(ns cake.tasks.test
  (:use cake cake.core
        [cake.classloader :only [with-test-classloader]]
        [bake.core :only [in-project-classloader?]]
        [bake.find-namespaces :only [find-namespaces-in-dir]]
        [useful.map :only [map-vals]]
        [clojure.pprint :only [pprint]]
        [clj-stacktrace.repl :as st]
        [clj-stacktrace.utils :as st-utils])
  (:require [com.georgejahad.difform :as difform]
            [clansi.core :as ansi])
  (:import [java.io File]))

(do
  ;; these functions were written by Brenton Ashworth
  ;; https://github.com/brentonashworth/lein-difftest
  (defn difform-str
    "Create a string that is the diff of the forms x and y."
    [x y]
    (subs
     (with-out-str
       (difform/clean-difform x y)) 1))

  (defmulti diff? (fn [form] (when (coll? form) (first form))))

  (defmethod diff? :default [form]
    false)

  (defmethod diff? 'not [form]
    (diff? (last form)))

  (defmethod diff? '= [form]
    (let [a (second form)
          b (last form)]
      (or (and (coll? a) (coll? b))
          (and (string? a) (string? b)))))

  (defn actual-diff
    "Transform the actual form that comes from clojure.test into a diff
   string. This will diff forms like (not (= ...)) and will return the string
   representation of anything else."
    [form]
    (if (diff? form)
      (let [[_ [_ actual expected]] form]
        (.trim (difform-str expected
                            actual)))
      form)))

(defn test-opts
  "figure out whichs tests to run based on cake's command line options"
  []
  (let [args (into (:test *opts*) (:autotest *opts*))]
    (merge {:tags #{} :functions #{} :namespaces #{}}
           (if (empty? args)
             {:namespaces true}
             (map-vals (->> (map read-string args)
                            (group-by #(cond (keyword?  %) :tags
                                             (namespace %) :functions
                                             :else         :namespaces)))
                       set)))))

(defn report-assertion-fail
  "report a test failure"
  [name {:keys [file line message expected actual]}]
  (println (ansi/style (format "FAIL in (%s) (%s:%d)" name file line) :red))
  (when [message] (println message))
  (println "expected:\n" expected "\nactual:\n" (actual-diff actual)))

;; this is a hack of clj-stacktrace.repl/pst-on
(defn report-assertion-error
  "report a test error"
  [name m]
  (letfn [(find-source-width [excp]
            (let [this-source-width (st-utils/fence
                                     (sort
                                      (map #(.length %)
                                           (map source-str (:trace-elems excp)))))]
              (if-let [cause (:cause excp)]
                (max this-source-width (find-source-width cause))
                this-source-width)))]
    (let [exec         (:actual m)
          source-width (find-source-width exec)]
      (st/pst-class-on *out* true (:class exec))
      (st/pst-message-on *out* true (:message exec))
      (st/pst-elems-on *out* true (:trace-elems exec) source-width)
      (if-let [cause (:cause exec)]
        (#'st/pst-cause-on *out* true cause source-width)))))

(defn report-test
  "reports an error or a failure for a test, or prints *out* if it exists."
  [ns test-result]
  (let [[name {:keys [out assertions]}] test-result
        {:keys [pass fail error]} (group-by :type assertions)]
    (when (or out fail error)
      (println)
      (println (ansi/style (str "cake test " ns "/" name) :yellow))
      (when out (print out))
      (when fail (doall (map (partial report-assertion-fail name) fail)))
      (when error (doall (map (partial report-assertion-error name) error))))))

(defn accumulate-assertions [acc [name {:keys [assertions]} :as all]]
  (let [acc (update-in acc [:test-count] inc)]
    (let [grouped (group-by :type assertions)
          fail-count (count (:fail grouped))
          pass-count (count (:pass grouped))
          error-count (count (:error grouped))]
      (-> acc
          (update-in [:fail-count] + fail-count)
          (update-in [:pass-count] + pass-count)
          (update-in [:error-count] + error-count)
          (update-in [:assertion-count] + fail-count pass-count error-count)))))

(defn report-ns
  "generate a summary for the namespace with `results`"
  [ns results]
  (println (ansi/style (apply str (repeat 40 " ")) :underline))
  (println (ansi/style (str"\ncake test " ns) :cyan))
  (doseq [test-result (doall (:tests results))]
    (report-test ns test-result))
  (let [{:keys [fail-count pass-count error-count assertion-count test-count] :as aggregate}
        (reduce accumulate-assertions
                {:ns ns, :test-count 0, :assertion-count 0, :pass-count 0, :fail-count 0, :error-count 0}
                (:tests results))]
    (println (format "\nRan %s tests containing %s assertions."
                     test-count
                     assertion-count))
    (println (ansi/style (format "%s failures, %s errors."
                                 fail-count
                                 error-count)
                         (if (= 0 (+ fail-count
                                     error-count))
                           :green
                           :red)))
    aggregate))

(defn run-project-tests
  "run the tests based on the command line options"
  [& opts]
  (with-test-classloader
    (bake-ns (:use bake.test clojure.test
                   [clojure.string :only [join]]
                   [bake.core :only [with-context in-project-classloader?]])
             (let [start (System/currentTimeMillis)
                   {:keys [ns-count test-count assertion-count pass-count fail-count error-count]}
                   (reduce (fn [acc {:keys [ns test-count assertion-count pass-count fail-count error-count] :as this-all}]
                             (-> acc
                                 (update-in [:ns-count] + 1)
                                 (update-in [:test-count] + test-count)
                                 (update-in [:assertion-count] + assertion-count)
                                 (update-in [:pass-count] + pass-count)
                                 (update-in [:fail-count] + fail-count)
                                 (update-in [:error-count] + error-count)))
                           {:ns-count 0, :test-count 0, :assertion-count 0, :pass-count 0, :fail-count 0, :error-count 0}
                           (for [[ns tests] (bake-invoke get-test-vars
                                                         (flatten (for [test-path (:test-path *project*)]
                                                                    (find-namespaces-in-dir (java.io.File. test-path))))
                                                         (merge (test-opts) (apply hash-map opts)))]
                             (report-ns ns (bake-invoke run-ns-tests ns tests))))]
               (let [summary (format "\nRan %d tests in %d namespaces, containing %d assertions, in %.2f seconds."
                                     test-count
                                     ns-count
                                     assertion-count
                                     (/ (- (System/currentTimeMillis)
                                           start)
                                        1000.0))]
                 (println (ansi/style (apply str (repeat (- (count summary) 2) " ")) :underline))
                 (println summary))
               (println (ansi/style (format "%d OK, %d failures, %d errors." pass-count fail-count error-count)
                                    (if (= 0 (+ fail-count error-count)) :green :red)))))))

(deftask test #{compile-java}
  "Run project tests."
  "Specify which tests to run as arguments like: namespace, namespace/function, or :tag"
  "Use --auto to automatically run tests whenever your project code changes."
  (if (:auto *opts*)
    (do (run-project-tests)
        (while true
          (run-project-tests :autotest true)))
    (run-project-tests)))
