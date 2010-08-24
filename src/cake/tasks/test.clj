(ns cake.tasks.test
  (:use cake cake.core)
  (:import [java.io File]))

(defn test-opts [args]
  (if (empty? args)
    {:all true}
    (group-by #(cond (keyword?  %) :tags
                     (namespace %) :functions
                     :else         :namespaces)
              (map read-string args))))

(deftask test #{compile}
  "Run project tests."
  (bake (:use bake.test) [opts (test-opts (:test *opts*))]
    (run-project-tests opts)))

(deftask autotest #{compile}
  "Automatically run tests whenever your project code changes."
  (bake (:use bake.test) [opts (test-opts (:autotest *opts*))]
    (run-project-tests (assoc opts :quiet true))))
