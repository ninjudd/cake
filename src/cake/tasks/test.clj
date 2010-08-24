(ns cake.tasks.test
  (:use cake cake.core)
  (:import [java.io File]))

(defn test-opts []
  (group-by
   #(cond (keyword?  %) :tags
          (namespace %) :functions
          :else         :namespaces)
   (map read-string (:test *opts*))))

(deftask test #{compile}
  "Run project tests."
  (bake (:use bake.test) [opts (test-opts)]
    (run-tests opts)))

(deftask autotest "Automatically run tests whenever your project code changes.")