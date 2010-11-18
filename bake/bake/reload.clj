(ns bake.reload
  "Try to reload files that have changed since the last reload. Based on lazytest.tracker."
  (:use [lazytest.reload :only [reload]]
        [lazytest.dependency :only [graph depend dependents remove-key depends?]]
        [lazytest.nsdeps :only [deps-from-ns-decl]]
        [cake.utils.find-namespaces :only [find-clojure-sources-in-dir read-file-ns-decl]]
        [clojure.set :only [union]]))

(defn- find-sources [dirs]
  (mapcat find-clojure-sources-in-dir dirs))

(defn- newer-than [timestamp files]
  (filter #(> (.lastModified %) timestamp) files))

(defn- newer-namespace-decls [timestamp dirs]
  (remove nil? (map read-file-ns-decl (newer-than timestamp (find-sources dirs)))))

(defn- add-to-dep-graph [dep-graph namespace-decls]
  (reduce (fn [g decl]
            (let [nn (second decl)
                  deps (deps-from-ns-decl decl)]
              (apply depend g nn deps)))
          dep-graph namespace-decls))

(defn- remove-from-dep-graph [dep-graph new-decls]
  (apply remove-key dep-graph (map second new-decls)))

(defn- update-dependency-graph [dep-graph new-decls]
  (-> dep-graph
      (remove-from-dep-graph new-decls)
      (add-to-dep-graph new-decls)))

(defn- affected-namespaces [changed-namespaces old-dependency-graph]
  (apply union (set changed-namespaces) (map #(dependents old-dependency-graph %)
                                             changed-namespaces)))

(defn reloader [classpath project-files jar-dir]
  (let [timestamp (atom (System/currentTimeMillis))
        graph     (atom (update-dependency-graph (graph) (newer-namespace-decls 0 classpath)))]
    (fn []
      (let [then @timestamp
            now  (System/currentTimeMillis)]
        (if-let [project-files (seq (newer-than then project-files))]
          (println "cannot reload project files:" project-files)
          (if-let [jars (seq (newer-than then (file-seq jar-dir)))]
            (println "jars have changed:" jars)
            (when-let [new-decls (seq (newer-namespace-decls then classpath))]
              (let [new-names (map second new-decls)
                    affected  (affected-namespaces new-names @graph)]
                (reset! timestamp now)
                (swap! graph update-dependency-graph new-decls)
                (if-let [to-reload (seq (filter find-ns affected))] ; only reload namespaces that are loaded
                  (if (contains? to-reload 'cake.core)
                    (println "cannot reload cake.core")
                    (if-let [cannot-reload (seq (filter #(depends? @graph % 'cake.core) to-reload))]
                      (println "cannot reload namespaces that depend on cake.core:" cannot-reload)
                      (apply reload to-reload))))))))))))
