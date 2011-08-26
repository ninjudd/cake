(ns bake.test
  (:use clojure.test
        [cake :only [*config*]]
        [bake.core :only [verbose? log as-fn]]
        [bake.reload :only [last-reloaded last-modified reload]]
        [bake.notify :only [notify]])
  (:import [java.io StringWriter IOException]))

(defn get-test-vars [namespaces opts]
  (let [[tags functions ns-opts] (map opts [:tags :functions :namespaces])
        ns-opts (as-fn ns-opts)
        run? (fn [ns]
               (if (ns-opts ns)
                 (if (ns-resolve ns 'test-ns-hook)
                   (comp #{'test-ns-hook} first)
                   (comp :test meta second))
                 (fn [[fn-name f]]
                   (or (some tags (:tags (meta f)))
                       (functions (apply symbol (map name [ns fn-name])))))))
        get-tests-for-ns (fn [ns]
                           (require ns)
                           (for [f (filter (run? ns) (ns-publics ns))]
                             (key f)))]
    (reduce (fn [acc ns]
              (if-let [test-fns (seq (get-tests-for-ns ns))]
                (assoc acc ns (doall test-fns))
                acc))
            {}
            namespaces)))

(declare *ns-results* *current-test*)

(defn update-results [m]
  (swap! *ns-results* update-in [:tests *current-test* :assertions] (fnil conj []) m))

(defmulti my-report :type)

(defmethod my-report :pass [m]
  (update-results (dissoc m :actual)))

(defmethod my-report :fail [m]
  (update-results m))

(defmethod my-report :error [m]
  (update-results m))

;; the methods below are never called because i'm calling test-var directly

(defmethod my-report :default [m]
  (prn :here))

(defmethod my-report :summary [m]
  (prn :here))

(defmethod my-report :begin-test-ns [m]
  (prn :here))

(defmethod my-report :end-test-ns [m]
  (prn :here))

(defmethod my-report :begin-test-var [m]
  (set! *current-test* (:name (meta (:var m))))
  (set! *out* (StringWriter.))
  (set! *err* *out*))

(defmethod my-report :end-test-var [m]
  (swap! *ns-results* assoc-in [:tests *current-test* :out] (not-empty (.toString *out*)))
  (set! *current-test* nil))

(defn run-ns-tests [ns tests]
  (let [ns-meta (meta (find-ns ns))
        each-fixtures (join-fixtures (:clojure.test/each-fixtures ns-meta))
        once-fixtures (join-fixtures (:clojure.test/once-fixtures ns-meta))]
    (require ns)
    (binding [report my-report
              *test-out* (StringWriter.)
              *out* *out* ;; this is so it gets restored
              *err* *err*
              *report-counters* (ref *initial-report-counters*)
              *ns-results* (atom {:tests {}})
              *current-test* nil]
      (if (= '(test-ns-hook) tests)
        ((var-get (ns-resolve ns 'test-ns-hook)))
        (once-fixtures
         (fn []
           (doseq [test tests]
             (each-fixtures
              (fn []
                (test-var (ns-resolve ns test))))))))
      @*ns-results*)))