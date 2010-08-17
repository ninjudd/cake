(ns bake.core
  (:use cake)
  (:require clojure.main            
            cake.project
            [cake.swank :as swank]
            [cake.server :as server])
  (:import [java.io FileOutputStream PrintStream PrintWriter]))

(defmacro defproject "Just save project hash in bake."
  [name version & args]
  (let [opts (apply hash-map args)]
    `(do (alter-var-root #'*project* (fn [_#] (cake.project/create '~name ~version '~opts))))))

(defmacro deftask "Just ignore deftask calls in bake."
  [name & body])

(defn quit []
  (if (= 0 (swank/num-connections))
    (server/quit)
    (println "refusing to quit because there are active swank connections")))

(defn project-eval [[ns ns-forms body]]
  (server/eval-multi `[(~'ns ~ns (:use ~'cake) ~@ns-forms) ~body]))

(defn start-server [port]
  (cake.project/init "project.clj")
  (server/redirect-to-log ".cake/project.log")
  (try (doseq [ns (:require *project*)]
         (require ns))
       (eval (:startup *project*))
       (catch Exception e
         (server/print-stacktrace e)))
  (when-let [auto-start (*config* "swank.auto-start")]    
    (swank/start auto-start))
  (server/create port project-eval :quit quit)
  nil)