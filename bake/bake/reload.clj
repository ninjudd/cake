(ns bake.reload
  "Try to reload files that have changed since the last reload. Adapted from Stuart Sierra's lazytest."
  (:use cake
        [clojure.set :only [difference]]
        [bake.core :only [in-project-classloader?]]
        [bake.dependency :only [graph]]
        [bake.nsdeps :only [newer-namespace-decls newer-than update-dependency-graph affected-namespaces]]
        [clojure.set :only [union]])
  (:import (java.io File)))

(defn reload-namespaces
  "Remove all specified namespaces then reload them."
  [& symbols]
  (doseq [sym symbols]
    (remove-ns sym))
  (dosync (alter @#'clojure.core/*loaded-libs* difference (set symbols)))
  (apply require symbols))

(def classpath
  (for [url (.getURLs (ClassLoader/getSystemClassLoader))]
    (File. (.getFile url))))

(def project-files
  (when-not (in-project-classloader?)
    (concat (map #(File. *root* %) ["project.clj" "context.clj" "tasks.clj"])
            (map #(File. *global-root* %) ["tasks.clj"]))))

(def timestamp
  (atom (System/currentTimeMillis)))

(def dep-graph
  (atom (update-dependency-graph (graph) (newer-namespace-decls 0 classpath))))

(defn reload-project-files
  ([] (reload-project-files project-files))
  ([files]
     (remove-ns 'tasks)
     (ns tasks
       (:use cake.core))
     (doseq [f files :when (.exists f) :let [f (.getPath f)]]
       (try (load-file f)
            (catch clojure.lang.Compiler$CompilerException e
              (when-not (= java.io.FileNotFoundException (class (.getCause e)))
                (throw e))
              (println "warning: could not load" f)
              (println " " (.getMessage e))
              (println "  if you've added a library to :dev-dependencies you must run 'cake deps' to install it"))))))

(defn reload []
  (let [last @timestamp
        now  (System/currentTimeMillis)]
    (when-let [new-decls (seq (newer-namespace-decls last classpath))]
      (let [new-names (map second new-decls)
            affected  (affected-namespaces new-names @dep-graph)]
        (reset! timestamp now)
        (swap! dep-graph update-dependency-graph new-decls)
        (when-let [to-reload (seq (filter find-ns affected))] ; only reload namespaces that are loaded
          (apply reload-namespaces to-reload))))
    (when (seq (newer-than last project-files))
      (reload-project-files project-files))))
