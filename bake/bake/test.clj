(ns bake.test
  (:use clojure.test
        [cake :only [*config*]]
        [bake.reload :only [last-reloaded reload]]
        [bake.notify :only [notify]])
  (:import [java.io StringWriter]))

(def last-tested (atom nil))

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

(defn run-project-tests [namespaces {autotest? :autotest :as opts}]
  (let [start    (System/nanoTime)
        run?     (test-pred opts)
        interval (* 1000 (Integer. (or (get *config* "autotest.interval") 5)))]
    (when autotest?
      (spit "/tmp/autotest" start)
      (while (and @last-tested (> @last-tested @last-reloaded))
        (reload)
        (print ".") (flush)
        (Thread/sleep interval)))
    (reset! last-tested (System/currentTimeMillis))
    (doseq [ns namespaces]
      (require ns)
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
            (when (or (not autotest?) (< 0 (apply + (map @*report-counters* [:fail :error]))))              
              (let [test-out (.toString *test-out*)]
                (print test-out) (flush)
                (when autotest?
                  (notify test-out))))))))
    (when-not autotest?
      (println "----")
      (println "Finished in" (/ (- (System/nanoTime) start) (Math/pow 10 9)) "seconds.\n"))))
