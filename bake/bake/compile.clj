(ns bake.compile
  (:use cake
        [bake.core :only [log]]
        [bake.reload :only [dep-graph classfile]]
        [bake.dependency :only [dependents]]
        [bake.find-namespaces :only [find-clojure-sources-in-dir read-file-ns-decl]]
        [clojure.set :only [union]])
  (:import (java.io File)))

(defn stale-namespaces [source-path]
  (let [aot (:aot *project*)
        compile?
        (if (= :all aot)
          (constantly true)
          (let [aot (set aot)]
            (fn [ns]
              (or (= ns (:main *project*))
                  (contains? aot ns)))))]
    (filter compile?
      (reduce (fn [stale sourcefile]
                (let [namespace (second (read-file-ns-decl sourcefile))
                      classfile (classfile namespace)]
                  (if (and (> (.lastModified sourcefile) (.lastModified classfile)))
                    (union stale (dependents @dep-graph namespace))
                    stale)))
              #{} (find-clojure-sources-in-dir (File. source-path))))))

(defn compile-stale [source-path compile-path]
  (binding [*compile-path* compile-path]
    (let [stale (stale-namespaces source-path)]
      (doseq [ns stale]
        (log "Compiling namespace" ns)
        (compile ns))
      (< 0 (count stale)))))
