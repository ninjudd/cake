(ns bake.compile
  (:use [bake.core :only [log print-stacktrace]]
        [bake.reload :only [dep-graph classfile]]
        [bake.dependency :only [dependents]]
        [bake.find-namespaces :only [find-clojure-sources-in-dir read-file-ns-decl]]
        [clojure.set :only [union]])
  (:import (java.io File)))


(defn stale-namespaces [source-path compile-path]
  (reduce (fn [stale sourcefile]
            (when-let [namespace (second (read-file-ns-decl sourcefile))]
              (if (> (.lastModified sourcefile)
                     (.lastModified (classfile compile-path namespace)))
                (union stale (conj (dependents @dep-graph namespace)
                                   namespace))
                stale)))
          #{} (mapcat #(find-clojure-sources-in-dir (File. %)) source-path)))

(defn compile-stale [source-path compile-path namespaces]
  (binding [*compile-path* compile-path]
    (let [stale    (stale-namespaces source-path compile-path)
          compile? (if (= :all namespaces)
                     (constantly true)
                     (partial contains? (set namespaces)))]
      (doseq [ns (filter compile? stale)]
        (log "Compiling namespace" ns)
        (try (compile ns)
             (catch ExceptionInInitializerError e
               (print-stacktrace e))))
      (< 0 (count stale)))))
