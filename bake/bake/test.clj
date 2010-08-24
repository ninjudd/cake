(ns bake.test
  (:refer-clojure :exclude [group-by])
  (:use [cake.contrib.find-namespaces :only [find-namespaces-in-dir]]
        useful)
  (:require [clojure.test :as test]
            [clojure.stacktrace :as stack]))

(defn test-pred [opts]
  (let [tags       (set (opts :tags))
        functions  (set (opts :functions))
        namespaces (set (opts :namespaces))]
    (fn [ns [name f]]
      (and (:test (meta f))
           (or (empty? opts)
               (namespaces ns)
               (some tags (:tags (meta f)))
               (tags (:tag (meta f)))
               (functions (symbol (str ns "/" name))))))))

(defn run-tests [opts]
  (let [start (System/nanoTime)
        run?  (test-pred opts)]
    (binding [test/*test-out* *out*]
      (doseq [ns (find-namespaces-in-dir (java.io.File. "test"))]
        (require ns)
        (when-let [tests (seq (filter (partial run? ns) (ns-publics ns)))]
          (let [ns-meta (meta (find-ns ns))
                once-fixtures (test/join-fixtures (::test/once-fixtures ns-meta))
                each-fixtures (test/join-fixtures (::test/each-fixtures ns-meta))]
            (binding [test/*report-counters* (ref test/*initial-report-counters*)]
              (test/report {:type :begin-test-ns :ns ns})
              (once-fixtures
               (fn []
                 (doseq [[name f] tests]
                   (each-fixtures #(test/test-var f)))))
              (test/report (assoc @test/*report-counters* :type :summary)))))))
    (println "----")
    (println "Finished in" (/ (- (System/nanoTime) start) (Math/pow 10 9)) "seconds.\n")))