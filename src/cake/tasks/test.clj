(ns cake.tasks.test
  (:use cake cake.core
        [bake.core :only [in-project-classloader?]]
        [cake.project :only [bake-ns bake-invoke]]
        [useful.map :only [map-vals]]
        [cake.project :only [with-test-classloader]]
        [bake.find-namespaces :only [find-namespaces-in-dir]]
        [clojure.pprint :only [pprint]])
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

(defn report [ns results]
  (prn :ns ns)
  (prn results))

(defn old-run-project-tests [& opts]
  (with-test-classloader
    (let [opts (merge (test-opts) (apply hash-map opts))
          tests-to-run (bake (:use bake.test clojure.test
                                   [bake.core :only [with-context]])
                             [namespaces (flatten (for [test-path (:test-path *project*)]
                                                    (find-namespaces-in-dir (java.io.File. test-path))))
                              opts opts]
                             (get-test-vars namespaces opts))]
      (doseq [[ns tests] tests-to-run]
        (report ns (bake (:use bake.test clojure.test
                                  [bake.core :only [with-context]])
                            [ns ns, tests tests]
                            (run-ns-tests ns tests)))))))

(defn run-project-tests [& opts]
  (bake-ns (:use bake.test clojure.test
                 [bake.core :only [with-context in-project-classloader?]])
           (prn :out (in-project-classloader?))
           (comment prn :in (bake-invoke in-project-classloader?))))

(deftask test #{compile-java}
  "Run project tests."
  "Specify which tests to run as arguments like: namespace, namespace/function, or :tag"
  (prn :cc cake/*classloader*)
  (run-project-tests))

(deftask autotest #{compile-java}
  "Automatically run tests whenever your project code changes."
  "Specify tests to run just like the test task. Specify the interval with --interval."
  (run-project-tests)
  (while true
    (run-project-tests :autotest true)))
