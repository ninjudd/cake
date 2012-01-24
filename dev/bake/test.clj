(ns bake.test
  (:use clojure.test
        [clojure.walk :only [postwalk]]
        [clojure.string :only [trim-newline]]
        [cake :only [*config*]]
        [bake.core :only [verbose? log all with-timing]]
        [bake.reload :only [last-reloaded last-modified reload]]
        [bake.notify :only [notify]]
        [bake.clj-stacktrace])
  (:import [java.io StringWriter IOException PrintWriter]))

(defn test-vars [nses {:keys [tags functions namespaces]}]
  (let [run? (fn [ns]
               (if (or (every? empty? [namespaces functions])
                       (namespaces ns))
                 (if (ns-resolve ns 'test-ns-hook)
                   (comp #{'test-ns-hook} first)
                   (comp :test meta second))
                 (fn [[fn-name f]]
                   (functions (apply symbol (map name [ns fn-name]))))))
        tags-match? (if (empty? tags)
                      (constantly true)
                      (fn [[fn-name f]]
                        (some tags (:tags (meta f)))))]
    (reduce (fn [vars ns]
              (require ns)
              (assoc vars
                ns (map key (filter (all (run? ns) tags-match?)
                                    (ns-publics ns)))))
            {} nses)))

(def ^{:dynamic true} *ns-results*)
(def ^{:dynamic true} *current-test*)
(def ^{:dynamic true} *string-writer*)

(defn reset-streams! []
  (set! *string-writer* (StringWriter.))
  (set! *out* (PrintWriter. *string-writer* true))
  (set! *err* *out*))

(defn unprintable? [obj]
  (not (get-method print-dup (type obj))))

(defn make-printable [obj]
  (if (unprintable? obj)
    (symbol (format "#=(symbol %s)" (pr-str (pr-str obj))))
    obj))

(defn update-results [& objects]
  (doseq [object objects]
    (when-let [results (if (instance? StringWriter object)
                         (do (reset-streams!)
                             (not-empty (trim-newline (.toString object))))
                         (-> (postwalk make-printable object)
                             (assoc :testing-contexts (testing-contexts-str))))]
      (swap! *ns-results* update-in [*current-test*] (fnil conj []) results))))

(defmulti my-report :type)

(defmethod my-report :pass [m]
  (update-results *string-writer* (dissoc m :actual)))

(defmethod my-report :fail [m]
  (update-results *string-writer* m))

(defmethod my-report :error [m]
  (update-results *string-writer* (update-in m [:actual] parse-exception)))

(defmethod my-report :begin-test-var [m]
  (set! *current-test* (:name (meta (:var m))))
  (reset-streams!))

(defmethod my-report :end-test-var [m]
  (update-results *string-writer*)
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
              *string-writer* nil
              *report-counters* (ref *initial-report-counters*)
              *ns-results* (atom {})
              *current-test* nil]
      (with-timing
        (if (= '(test-ns-hook) tests)
          ((var-get (ns-resolve ns 'test-ns-hook)))
          (once-fixtures
           (fn []
             (doseq [test tests]
               (each-fixtures
                (fn []
                  (test-var (ns-resolve ns test))))))))
        @*ns-results*))))
