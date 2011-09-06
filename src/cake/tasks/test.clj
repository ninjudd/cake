(ns cake.tasks.test
  (:use cake cake.core
        [bake.core :only [in-project-classloader?]]
        [cake.project :only [bake-ns bake-invoke]]
        [useful.map :only [map-vals]]
        [cake.project :only [with-test-classloader]]
        [bake.find-namespaces :only [find-namespaces-in-dir]]
        [clojure.pprint :only [pprint]]
        [clojure.string :only [trim-newline]]
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

(defn accumulate-assertions [acc [name assertions]]
  (let [acc         (update-in acc [:test-count] inc)
        grouped     (group-by  :type assertions)
        fail-count  (count     (:fail grouped))
        pass-count  (count     (:pass grouped))
        error-count (count     (:error grouped))]
    (-> acc
        (update-in [:fail-count]      + fail-count)
        (update-in [:pass-count]      + pass-count)
        (update-in [:error-count]     + error-count)
        (update-in [:assertion-count] + fail-count
                                        pass-count
                                        error-count))))

(defn parse-ns-results
  "generate a summary datastructure for the namespace with `results`"
  [ns results]
  {:ns ns
   :type :ns
   :aggregates (reduce accumulate-assertions
                       {:test-count      0
                        :assertion-count 0
                        :pass-count      0
                        :fail-count      0
                        :error-count     0}
                       results)
   :tests (remove nil? (for [[test assertions] results]
                         (when-let [display (seq
                                             (remove #(= :pass (:type %))
                                                     assertions))]
                           {:name    test
                            :display display})))})

(defn printfs [style formatter & args]
  (println (apply ansi/style (apply format formatter args) style)))

(defmulti report! :type)

(defmethod report! :default [object]
  (println object "\n"))

(defmethod report! :fail [{:keys [file line message expected actual testing-contexts] :as m}]
  (printfs [:red] "FAIL! in %s:%d" file line)
  (println (str (when testing-contexts (str testing-contexts "\n"))
                (when message (str message "\n"))
                " expected:\n" expected "\n actual:\n" (actual-diff actual) "\n")))

;; this is a hack of clj-stacktrace.repl/pst-on
(defmethod report! :error [m]
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

(defn colorize [& args]
  (vector (if (= 0 (apply + args))
            :green
            :red)))

(defmethod report! :ns [results]
  (let [ns         (:ns results)
        aggregates (:aggregates results)
        {:keys [test-count assertion-count fail-count error-count]} aggregates]

    (printfs [:cyan] (str "cake test " ns "\n"))
    (doseq [{:keys [name] :as test} (:tests results)]
      (printfs [:yellow] (str "cake test " ns "/" name))
      (doseq [object (:display test)]
        (report! object)))
    (printfs [] "Ran %s tests containing %s assertions." test-count assertion-count)
    (printfs (colorize fail-count error-count)
             "%s failures, %s errors."
             fail-count
             error-count)
    (printfs [:underline] (apply str (repeat 40 " ")))
    (println)))

(defn display-and-aggregate-ns [acc {{:keys [test-count assertion-count pass-count fail-count error-count]}
                                     :aggregates :as results}]
  (report! results)
  (-> acc
      (update-in [:ns-count]        + 1)
      (update-in [:test-count]      + test-count)
      (update-in [:assertion-count] + assertion-count)
      (update-in [:pass-count]      + pass-count)
      (update-in [:fail-count]      + fail-count)
      (update-in [:error-count]     + error-count)))

(defn run-project-tests
  "run the tests based on the command line options"
  [& opts]
  (println)
  (with-test-classloader
    (bake-ns (:use bake.test clojure.test
                   [clojure.string :only [join]]
                   [bake.core :only [with-context in-project-classloader?]])
             (let [start (System/currentTimeMillis)
                   {:keys [ns-count test-count assertion-count pass-count fail-count error-count]}
                   (reduce display-and-aggregate-ns
                           {:ns-count        0
                            :test-count      0
                            :assertion-count 0
                            :pass-count      0
                            :fail-count      0
                            :error-count     0}
                           (for [[ns tests] (bake-invoke get-test-vars
                                                         (flatten (for [test-path (:test-path *project*)]
                                                                    (find-namespaces-in-dir (java.io.File. test-path))))
                                                         (merge (test-opts)
                                                                (apply hash-map opts)))]
                             (parse-ns-results
                              ns
                              (bake-invoke
                               run-ns-tests
                               ns
                               tests))))]
               (printfs [] "Ran %d tests in %d namespaces, containing %d assertions, in %.2f seconds."
                        test-count
                        ns-count
                        assertion-count
                        (/ (- (System/currentTimeMillis)
                              start)
                           1000.0))
               (printfs (colorize fail-count error-count)
                        "%d OK, %d failures, %d errors."
                        pass-count
                        fail-count
                        error-count)))))

(deftask test #{compile-java}
  "Run project tests."
  "Specify which tests to run as arguments like: namespace, namespace/function, or :tag"
  (run-project-tests))

(deftask autotest #{compile-java}
  "Automatically run tests whenever your project code changes."
  "Specify tests to run just like the test task. Specify the interval with --interval."
  (run-project-tests)
  (while true
    (run-project-tests :autotest true)))
