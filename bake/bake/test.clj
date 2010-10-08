(ns bake.test
  (:use clojure.test)
  (:import [java.io StringWriter]))

(defn test-pred [opts]
  (let [tags       (set (opts :tags))
        functions  (set (opts :functions))
        namespaces (set (opts :namespaces))]
    (fn [ns [name f]]
      (and (:test (meta f))
           (or (:all opts)
               (namespaces ns)
               (some tags (:tags (meta f)))
               (functions (symbol (str ns "/" name))))))))

(defn run-project-tests [namespaces opts]
  (let [start (System/nanoTime)
        run?  (test-pred opts)]
    (doseq [ns namespaces]
      (require :reload-all ns)
      (when-let [tests (seq (filter (partial run? ns) (ns-publics ns)))]
        (let [ns-meta (meta (find-ns ns))
              once-fixtures (join-fixtures (:clojure.test/once-fixtures ns-meta))
              each-fixtures (join-fixtures (:clojure.test/each-fixtures ns-meta))]
          (binding [*test-out* (StringWriter.)
                    *report-counters* (ref *initial-report-counters*)]
            (report {:type :begin-test-ns :ns ns})
            (once-fixtures
             (fn []
               (doseq [[name f] tests]
                 (each-fixtures #(test-var f)))))
            (report (assoc @*report-counters* :type :summary))
            (when (or (not (:quiet opts)) (< 0 (apply + (map @*report-counters* [:fail :error]))))
              (print (.toString *test-out*))
              (flush))))))
    (when-not (:quiet opts)
      (println "----")
      (println "Finished in" (/ (- (System/nanoTime) start) (Math/pow 10 9)) "seconds.\n"))))