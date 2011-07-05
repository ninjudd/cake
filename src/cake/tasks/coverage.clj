(ns cake.tasks.coverage
  (:use cake [cake.core :rename {bake core-bake}]
        [cake.file :only [file]]
        [useful.debug :only [?]]
        [clojure.tools.namespace :only [find-namespaces-in-dir
                                        find-clojure-sources-in-dir]]
        [clojure.java.io :only [reader]]))

(def fake-bake false)
(defmacro bake [& args]
  (if fake-bake
    (last args)
    `(core-bake ~@args)))

(defn testable-namespaces []
  (mapcat (fn [dir]
            (find-namespaces-in-dir (java.io.File. dir)))
          (:source-path *project*)))

(defn testable-symbols [namespaces]
  (bake (:use [useful.debug :only [?]])
    [namespaces namespaces]
    (for [n namespaces
          [sym var] (ns-publics (find-ns (doto n require)))
          :when (and (bound? var) (fn? @var) (-> var meta (get :needs-test true)))]
      (apply symbol (map str [n sym])))))

(defn clj-test-files []
  (apply concat (map (comp find-clojure-sources-in-dir file)
                     (:test-path *project*))))

(let [end (Object.)]
  (defn read-all [reader]
    (try (doall
          (take-while (complement #{end})
                      (repeatedly #(read reader false end))))
         (catch Throwable _))))

(defn used-symbols [file]
  (filter symbol? (flatten (read-all (java.io.PushbackReader. (reader file))))))

(defn un-tested-symbols []
  (let [targets (testable-symbols (testable-namespaces))
        tested (set (mapcat used-symbols (clj-test-files)))
        untested (remove (comp tested symbol name) targets)]
    (into {} (for [[ns syms] (group-by namespace untested)]
               [ns (map name syms)]))))

(deftask coverage
  (when-let [needed-additions (not-empty (un-tested-symbols))]
    (print "The following functions appear to be untested:")
    (let [width (apply max (map #(.length (key %)) needed-additions))
          fmt (str "%n  %" width "s:")]
      (doseq [[ns syms] needed-additions]
        (print (format fmt ns))
        (doseq [s syms] (print (str " " s))))
      (.flush *out*)))) ;; since we're not getting println's auto-flush
