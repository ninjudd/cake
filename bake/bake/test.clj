(ns bake.test
  (:use clojure.test
        [cake :only [*config*]]
        [bake.reload :only [last-reloaded last-modified reload]]
        [bake.notify :only [notify]])
  (:import [java.io StringWriter IOException]))

(def last-passed    (atom (System/currentTimeMillis)))
(def last-tested    (atom (System/currentTimeMillis)))
(def last-exception (atom 0))

(defn wait-for-reload [interval]
  (while (or (< @last-reloaded @last-tested)
             (< @last-reloaded @last-exception))
    (Thread/sleep interval)
    (try (print ".") (flush)
         (reload)
         (reset! last-exception 0)
         (catch Throwable e
           (when (instance? IOException e) (throw e))
           (when (> @last-modified @last-exception)
             (notify (str (class e) ": " (.getMessage e))))
           (reset! last-exception (System/currentTimeMillis))))))

(defn run? [opts ns]
  (let [tags       (set (opts :tags))
        functions  (set (opts :functions))
        namespaces (set (opts :namespaces))]
    (fn [[name f]]
      (and (:test (meta f))
           (or (:all opts)
               (namespaces ns)
               (some tags (:tags (meta f)))
               (functions (symbol (str ns "/" name))))))))

(defn- tests-with-fixtures [opts ns]
  (let [tests  (seq (filter (run? opts ns) (ns-publics ns)))
        ns-meta (meta (find-ns ns))
        once-fixtures (join-fixtures (:clojure.test/once-fixtures ns-meta))
        each-fixtures (join-fixtures (:clojure.test/each-fixtures ns-meta))]
    (once-fixtures
      (fn []
        (doseq [[name f] tests]
          (each-fixtures #(test-var f)))))))

(defn run-ns-tests [opts ns]
  (require ns)
  (binding [*test-out* (StringWriter.)
            *report-counters* (ref *initial-report-counters*)]

    (report {:type :begin-test-ns :ns ns})
    (let [hook (get (ns-publics ns) 'test-ns-hook)]
      (if (and (:test-ns-hook? opts) 
               hook)
        (hook)
        (tests-with-fixtures opts ns)))
    (report (assoc @*report-counters* :type :summary))

    (let [failed?  (< 0 (apply + (map @*report-counters* [:fail :error])))
          test-out (.toString *test-out*)]
      (if (:autotest opts)
        (when failed?
          (notify test-out))
        (do (print test-out)
          (flush)))
      failed?)))

(defn- project-test-runner [namespaces opts]
  (cond
    ; when the test task is run with no options then all test nses 
    ; should be run and the test-ns-hook should be obeyed
    (:all opts) 
    (map (partial run-ns-tests opts) namespaces)

    ; when the test task is run specifying a ns then the specified ns 
    ; should be run and the test-ns-hook should be obeyed
    (:namespaces opts)
    (map (partial run-ns-tests opts) (:namespaces opts))

    ; when tests are tagged and tags are specified then the tagged 
    ; tests should be run and test-ns-hook should be ignored 
    ; (with a warning)
    (:tags opts)
    (do
      (println "Warning: only running tagged tests  and disregarding the test-ns-hook.")
      (map (partial run-ns-tests (assoc opts :test-ns-hook? false))
           (filter (fn [ns]
                     (let [ns-tags (mapcat (comp :tags meta) 
                                           (vals (ns-publics ns)))]
                       (some (set (:tags opts)) ns-tags)))
                   namespaces)))

    ; when a specific test is specified then the specified test should 
    ; be run and test-ns-hook should be ignored (with a warning)
    (:functions opts)
    (do
      (println "Warning: running specified tests only, disregarding the test-ns-hook.")
      (map (partial run-ns-tests (assoc opts :test-ns-hook? false))
           (map (comp symbol namespace) (:functions opts))))

    :default
    (map (partial run-ns-tests opts) namespaces)))

(defn run-project-tests [namespaces opts]
  (when (:autotest opts)
    (wait-for-reload (* 1000 (Integer. (or (get *config* "autotest.interval") 5)))))
  (let [start    (System/currentTimeMillis)
        results  (project-test-runner namespaces opts)
        failures (count (remove not results))]
    (when (= 0 failures)
      (when (and (:autotest opts)
                 (< @last-passed @last-tested))
        (notify "All tests passed"))
      (reset! last-passed start))
    (reset! last-tested start)
    (when-not (:autotest opts)
      (println "----")
      (println "Finished in" (/ (- (System/currentTimeMillis) start) 1000.0) "seconds.\n"))))
