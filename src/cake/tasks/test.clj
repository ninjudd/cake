(ns cake.tasks.test
  (:use cake)
  (:import [java.io File]))

(deftask test #{compile}
  "Run project tests."
  (bake (:use bake.test) []
    (let [nses  (all-test-namespaces project)]
      (doseq [ns nses] (require ns))
      (binding [clojure.test/*test-out* *out*]
        (let [grouped-tests (get-grouped-tests nses opts)]
          (let [results (if (:auto opts)
                          (run-tests-for-auto grouped-tests)
                          (concat
                           (run-tests-for-fns  grouped-tests)
                           (run-tests-for-nses grouped-tests)
                           (run-tests-for-tags grouped-tests (all-test-namespaces project))))]
            (if (> (count results) 1)
              (-> (apply merge-with + results) (assoc :type :summary) clojure.test/report))))))))
