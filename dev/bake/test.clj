(ns bake.test
  (:use clojure.test
        [clojure.walk :only [postwalk]]
        [clojure.string :only [trim-newline]]
        [cake :only [*config*]]
        [bake.core :only [verbose? log all with-timing]]
        [bake.reload :only [last-reloaded last-modified reload]]
        [bake.notify :only [notify]]
        [bake.clj-stacktrace]
        [useful.fn :only [to-fix given]])
  (:import [java.io StringWriter IOException]))

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

(declare *ns-results* *current-test*)

(defn reset-streams! []
  (set! *out* (StringWriter.))
  (set! *err* *out*))

(defn unprintable? [obj]
  (not (get-method print-dup (type obj))))

(defn make-printable [obj]
  (symbol (format "#=(symbol %s)" (pr-str (pr-str obj)))))

(defn update-results [& objects]
  (doseq [object objects]
    (let [object (if (instance? StringWriter object)
                   (do (reset-streams!)
                       (not-empty (trim-newline (.toString object))))
                   (-> (postwalk (to-fix unprintable? make-printable) object)
                       (given (seq *testing-contexts*)
                              assoc :testing-contexts (testing-contexts-str))))]
      (when object
        (swap! *ns-results* update-in [*current-test*] (fnil conj []) object)))))

(defmulti my-report :type)

(defmethod my-report :pass [m]
  (update-results *out* (dissoc m :actual)))

(defmethod my-report :fail [m]
  (update-results *out* m))

(defmethod my-report :error [m]
  (update-results *out* (update-in m [:actual] parse-exception)))

(defmethod my-report :begin-test-var [m]
  (set! *current-test* (:name (meta (:var m))))
  (reset-streams!))

(defmethod my-report :end-test-var [m]
  (update-results *out*)
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
