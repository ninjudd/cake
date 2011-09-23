(ns cake.tasks.test
  (:use cake cake.core
        [cake.file :only [file]]
        [cake.classloader :only [with-test-classloader]]
        [bake.core :only [in-project-classloader? with-timing]]
        [bake.find-namespaces :only [find-namespaces-in-dir]]
        [useful.utils :only [adjoin]]
        [useful.map :only [map-vals]]
        [clojure.pprint :only [pprint]]
        [clojure.string :only [trim-newline]]
        [clj-stacktrace.repl :as st]
        [clj-stacktrace.utils :as st-utils])
  (:refer-clojure :exclude [+])
  (:require [com.georgejahad.difform :as difform]
            [clansi.core :as ansi])
  (:import [java.io File]))

(def + (fnil clojure.core/+ 0 0))

(do ;; written by Brenton Ashworth (https://github.com/brentonashworth/lein-difftest)
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
  "Parse the test command line args."
  [args]
  (adjoin {:tags #{} :functions #{} :namespaces #{}}
          (group-by #(cond (keyword?  %) :tags
                           (namespace %) :functions
                           :else         :namespaces)
                    (map read-string args))))

(defn printfs [style formatter & args]
  (println (apply ansi/style (apply format formatter args) style)))

(defn colorize [count]
  (vector (if (= 0 (+ (:fail count) (:error count)))
            :green
            :red)))

(defmulti report! :type)

(defmethod report! :default [object]
  (println object "\n"))

(defmethod report! :fail [{:keys [file line message expected actual testing-contexts] :as m}]
  (printfs [:red] "FAIL! in %s:%d" file line)
  (println (str (when testing-contexts (str testing-contexts "\n"))
                (when message (str message "\n"))
                " expected:\n" expected "\n actual:\n" (actual-diff actual) "\n")))

(defmethod report! :error [m] ;; this is a hack of clj-stacktrace.repl/pst-on
  (letfn [(find-source-width [excp]
            (let [this-source-width (st-utils/fence
                                     (sort
                                      (map (comp #(.length %) source-str)
                                           (:trace-elems excp))))]
              (if-let [cause (:cause excp)]
                (max this-source-width (find-source-width cause))
                this-source-width)))]
    (let [exec         (:actual m)
          source-width (find-source-width exec)]
      (st/pst-class-on   *out* true (:class exec))
      (st/pst-message-on *out* true (:message exec))
      (st/pst-elems-on   *out* true (:trace-elems exec) source-width)
      (if-let [cause (:cause exec)]
        (#'st/pst-cause-on *out* true cause source-width))))
  (println))

(defmethod report! :ns [{:keys [ns count tests]}]
  (printfs [:cyan] (str "cake test " ns "\n"))
  (doseq [{:keys [name output] :as test} tests :when output]
    (printfs [:yellow] (str "cake test " ns "/" name))
    (dorun (map report! output)))
  (printfs [] "Ran %s tests containing %s assertions in %.2fs"
           (:test      count 0)
           (:assertion count 0)
           (/ (:time count) 1000.0))
  (printfs (colorize count)
           "%s failures, %s errors"
           (:fail  count 0)
           (:error count 0))
  (printfs [:underline] (apply str (repeat 40 " ")))
  (println))

(defn accumulate-assertions [acc [name assertions]]
  (let [counts (map-vals (group-by :type assertions) count)]
    (merge-with + acc
                (assoc counts
                  :test      1
                  :assertion (reduce + (vals counts))))))

(defn parse-results
  "Generate a summary datastructure for the namespace with results."
  [ns [results time]]
  {:ns ns
   :type :ns
   :count (assoc (reduce accumulate-assertions {} results)
            :time time
            :ns   1)
   :tests (for [[test result] results]
            {:name   test
             :output (seq (remove (comp #{:pass} :type)
                                  result))})})

(defn display-and-aggregate
  [acc {count :count :as results}]
  (report! results)
  (merge-with + acc count))

(defn test-vars
  "Determine which tests to run in the project JVM."
  [opts]
  (let [test-files (mapcat (comp find-namespaces-in-dir file) (:test-path *project*))]
    (bake-invoke test-vars test-files opts)))

(defn run-project-tests
  "Run the tests based on the command line options."
  [opts]
  (println)
  (with-test-classloader
    (bake-ns (:use bake.test clojure.test
                   [clojure.string :only [join]]
                   [bake.core :only [with-context in-project-classloader?]])
             (let [[count real-time] (with-timing
                                       (reduce display-and-aggregate {}
                                               (for [[ns tests] (test-vars opts) :when (seq tests)]
                                                 (parse-results ns (bake-invoke run-ns-tests ns tests)))))]
               (if (< 0 (:test count 0))
                 (do (printfs [] "Ran %d tests in %d namespaces, containing %d assertions, in %.2fs (%.2fs real)"
                              (:test      count 0)
                              (:ns        count 0)
                              (:assertion count 0)
                              (/ (:time count) 1000.0)
                              (/ real-time     1000.0))
                     (printfs (colorize count)
                              "%d OK, %d failures, %d errors"
                              (:pass  count 0)
                              (:fail  count 0)
                              (:error count 0)))
                 (printfs [:red] "No tests matched arguments"))))))

(deftask test #{compile-java}
  "Run project tests."
  "Specify which tests to run as arguments like: namespace, namespace/function, or :tag"
  "Use --auto to automatically run tests whenever your project code changes."
  {auto? :auto args :test}
  (let [opts (test-opts args)]
    (if (:auto *opts*)
      (do (run-project-tests opts)
          (while true
            (run-project-tests opts)))
      (run-project-tests opts))))
