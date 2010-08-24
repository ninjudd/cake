(ns cake.tasks.test
  (:use cake cake.core)
  (:import [java.io File]))

(defn test-opts []
  (let [args (into (:test *opts*) (:autotest *opts*))]
    (if (empty? args)
      {:all true}
      (group-by #(cond (keyword?  %) :tags
                       (namespace %) :functions
                       :else         :namespaces)
                (map read-string args)))))

(deftask test #{compile}
  "Run project tests."
  "Specify which tests to run as arguments like: namespace, namespace/function, or :tag"
  (bake (:use bake.test) [opts (test-opts)]
    (run-project-tests opts)))

(deftask autotest #{compile}
  "Automatically run tests whenever your project code changes."
  "Specify tests to run just like the test task. Specify the interval with --interval."
  (bake (:use bake.test) [opts (test-opts)]
    (run-project-tests (assoc opts :quiet true))))
