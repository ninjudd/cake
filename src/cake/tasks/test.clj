(ns cake.tasks.test
  (:use cake cake.core
        [bake.find-namespaces :only [find-namespaces-in-dir]])
  (:import [java.io File]))

(defn test-opts []
  (let [args (into (:test *opts*) (:autotest *opts*))]
    (if (empty? args)
      {:all true}
      (group-by #(cond (keyword?  %) :tags
                       (namespace %) :functions
                       :else         :namespaces)
                (map read-string args)))))

(defn run-project-tests [& opts]
  (bake (:use bake.test
              [bake.core :only [with-context]])
    [namespaces (find-namespaces-in-dir (java.io.File. "test"))
     opts       (merge {:test-ns-hook? true}
                       (test-opts) 
                       (apply hash-map opts))]
    (with-context :test
      (run-project-tests namespaces opts))))

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
