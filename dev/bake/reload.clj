(ns bake.reload
  "Try to reload files that have changed since the last reload. Adapted from Stuart Sierra's lazytest."
  (:use cake
        [bake.core :only [in-project-classloader?]]
        [bake.dependency :only [graph]]
        [bake.nsdeps :only [newer-namespace-decls newer-than update-dependency-graph affected-namespaces]]
        [clojure.set :only [union difference]])
  (:import (java.io File)))

(defn classfile
  ([ns] (classfile (first (:compile-path *project*)) ns))
  ([dir ns]
     (File. dir
            (.. (str ns)
                (replace "-" "_")
                (replace "." "/")
                (concat "__init.class")))))

(defn reload-namespaces
  "Remove all specified namespaces then reload them."
  [& symbols]
  (doseq [sym symbols]
    (remove-ns sym))
  (dosync (alter @#'clojure.core/*loaded-libs* difference (set symbols)))
  (apply require symbols))

(def classpath
  (for [url (.getURLs (.getClassLoader clojure.lang.RT))]
    (File. (.getFile url))))

(def task-files
  (map #(File. % "tasks.clj") [*root* *global-root*]))

(def project-files
  (concat (map #(File. *root* %) ["project.clj" "context.clj" ".cake/context.clj"])
          [(File. *global-root* "context.clj")]))

(def last-modified (atom (System/currentTimeMillis)))
(def last-reloaded (atom (System/currentTimeMillis)))

(def dep-graph
  (atom (update-dependency-graph (graph) (newer-namespace-decls 0 classpath))))

(defn load-files [files]
  (doseq [f files :when (.exists f) :let [f (.getPath f)]]
    (try (load-file f)
         (catch clojure.lang.Compiler$CompilerException e
           (when-not (= java.io.FileNotFoundException (class (.getCause e)))
             (throw e))
           (println "warning: could not load" f)
           (println " " (.getMessage e))
           (println "  if you've added a library to :dev-dependencies you must run 'cake deps' to install it")))))

(defn reload-project-files
  ([] (reload-project-files (concat project-files task-files)))
  ([files]
     (remove-ns 'tasks)
     (ns tasks
       (:use cake cake.core))
     (load-files files)))

(defn- reload? [ns]
  ; only reload namespaces that are loaded and not aot-compiled
  (and (find-ns ns)
       (not (.exists (classfile ns)))))

(defn reload []
  (let [last @last-modified
        now  (System/currentTimeMillis)]
    (seq
     (doall
      (concat
       (when-let [new-decls (seq (newer-namespace-decls last classpath))]
         (let [new-names (map second new-decls)
               affected  (affected-namespaces new-names @dep-graph)]
           (swap! dep-graph update-dependency-graph new-decls)
           (when-let [to-reload (seq (filter reload? affected))]
             (reset! last-modified now)
             (apply reload-namespaces to-reload)
             (reset! last-reloaded now)
             to-reload)))
       (when-let [modified (seq (newer-than last project-files))]
         (reset! last-modified now)
         (when-not (in-project-classloader?)
           (reload-project-files))
         (reset! last-reloaded now)
         modified))))))
