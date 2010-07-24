(ns cake
  (:use bake.project)
  (:require clojure.main            
            [bake.swank :as swank]
            [bake.server :as server])
  (:import [java.io FileOutputStream PrintStream PrintWriter]))

(defonce cake-project (atom nil))
(def project nil)

(defmacro defproject "Just save project hash in cake."
  [name version & args]
  (let [opts (apply hash-map args)]
    `(do (compare-and-set! cake-project nil (create-project '~name ~version '~opts)))))

(defmacro deftask "Just ignore deftask calls in cake."
  [name & body])

(defn eval-multi [form]
  (clojure.main/with-bindings
    (binding [project @cake-project]
      (if (vector? form)
        (doseq [f form] (eval f))
        (eval form)))))

(defn quit []
  (if (= 0 (swank/num-connections))
    (server/quit)
    (println "refusing to quit because there are active swank connections")))

(defn startup [project]
  (System/setErr (PrintStream. (FileOutputStream. ".bake/cake.log")))
  (binding [*err* (PrintWriter. System/err true)]
    (try (doseq [ns (:require project)]
           (require ns))
         (eval (:startup project))
         (catch Exception e
           (server/print-stacktrace e)))))

(defn start-server [port]
  (init "project.clj")
  (startup @cake-project)  
  (server/create port eval-multi :quit quit)
  (when-let [opts (swank/config)]
    (when-not (= false (:auto-start opts))
      (swank/start opts)))
  nil)
