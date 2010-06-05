(ns cake.tasks.test
  (:use cake)
  (:import [java.io File]))

(deftask test
  (bake 
   (require 'clojure.test)
   (use 'bake.test)
   (let [start (System/nanoTime)]
     (doseq [ns (all-test-namespaces project)] (require ns))
     (let [grouped-tests (get-grouped-tests project opts)]
       (let [results (concat
                      (run-tests-for-fns grouped-tests)
                      (run-tests-for-nses grouped-tests)
                      (run-tests-for-tags grouped-tests (all-test-namespaces project)))]
         (if (> (count results) 1)
           (-> (apply merge-with + results) (assoc :type :summary) clojure.test/report))))
     
     (println "Finished in" (/ (- (System/nanoTime) start) (Math/pow 10 9)) "seconds.\n"))))
