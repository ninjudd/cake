(ns cake.tasks.test
  (:use cake cake.core
        [bake.core :only [in-project-classloader?]]
        [cake.project :only [bake-ns bake-invoke]]
        [useful.map :only [map-vals]]
        [cake.project :only [with-test-classloader]]
        [bake.find-namespaces :only [find-namespaces-in-dir]]
        [clojure.pprint :only [pprint]])
  (:require [com.georgejahad.difform :as difform]
            [clansi.core :as ansi])
  (:import [java.io File]))

(defn test-opts []
  (let [args (into (:test *opts*) (:autotest *opts*))]
    (merge {:tags #{} :functions #{} :namespaces #{}}
           (if (empty? args)
             {:namespaces true}
             (->> (map read-string args)
                  (group-by #(cond (keyword?  %) :tags
                                   (namespace %) :functions
                                   :else         :namespaces))
                  (map-vals set))))))

;; this method by brenton ashworth
(defn difform-str
  "Create a string that is the diff of the forms x and y."
  [x y]
  (subs
   (with-out-str
     (difform/clean-difform x y)) 1))

;; this one too
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

;; this one too
(defn actual-diff
  "Transform the actual form that comes from clojure.test into a diff
   string. This will diff forms like (not (= ...)) and will return the string
   representation of anything else."
  [form]
  (if (diff? form)
    (let [[_ [_ actual expected]] form]
          (difform-str expected
                       actual))
    form))

(defn report-fail [name m]
  (println (format "FAIL in (%s) (%s:%d)" name (:file m) (:line m)))
  (when-let [message (:message m)]
    (println message))
  (println "expected:" (:expected m))
  (println "actual:\n" (actual-diff (:actual m))))

(defn report-test [ns [name {:keys [out assertions]}]]
  (let [{:keys [pass fail]} (group-by :type assertions)]
    (when (or out fail)
      (println)
      (println (str ns "/" name))
      (when out (print out))
      (when fail (doall (map (partial report-fail name) fail))))))

(defn report-ns [ns results]
  (println "\ncake test" ns)
  (doseq [test-result (:tests results)]
    (report-test ns test-result)))

(defn run-project-tests [& opts]
  (with-test-classloader
    (bake-ns (:use bake.test clojure.test
                   [clojure.string :only [join]]
                   [bake.core :only [with-context in-project-classloader?]])
             (doseq [[ns tests] (bake-invoke get-test-vars
                                             (flatten (for [test-path (:test-path *project*)]
                                                        (find-namespaces-in-dir (java.io.File. test-path))))
                                             (merge (test-opts) (apply hash-map opts)))]
               (report-ns ns (bake-invoke run-ns-tests ns tests))))))

(deftask test #{compile-java}
  "Run project tests."
  "Specify which tests to run as arguments like: namespace, namespace/function, or :tag"
  (prn :style-test)
  (println (ansi/style-test-page))
  (run-project-tests))

(deftask autotest #{compile-java}
  "Automatically run tests whenever your project code changes."
  "Specify tests to run just like the test task. Specify the interval with --interval."
  (run-project-tests)
  (while true
    (run-project-tests :autotest true)))
