(ns bake
  (:use cake.project)
  (:require clojure.main            
            [cake.swank :as swank]
            [cake.server :as server])
  (:import [java.io FileOutputStream PrintStream PrintWriter]))

(defonce bake-project (atom nil))
(def project nil)

(defmacro defproject "Just save project hash in bake."
  [name version & args]
  (let [opts (apply hash-map args)]
    `(do (compare-and-set! bake-project nil (create-project '~name ~version '~opts)))))

(defmacro deftask "Just ignore deftask calls in bake."
  [name & body])

(defn eval-multi [form]
  (clojure.main/with-bindings
    (binding [project @bake-project]
      (if (vector? form)
        (doseq [f form] (eval f))
        (eval form)))))

(defn quit []
  (if (= 0 (swank/num-connections))
    (server/quit)
    (println "refusing to quit because there are active swank connections")))

(defn startup [project]
  (System/setErr (PrintStream. (FileOutputStream. ".cake/bake.log")))
  (binding [*err* (PrintWriter. System/err true)]
    (try (doseq [ns (:require project)]
           (require ns))
         (eval (:startup project))
         (catch Exception e
           (server/print-stacktrace e)))))

(defn start-server [port]
  (init "project.clj")
  (startup @bake-project)  
  (server/create port eval-multi :quit quit)
  (when-let [opts (swank/config)]
    (when-not (= false (:auto-start opts))
      (swank/start opts)))
  nil)
