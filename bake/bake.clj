(ns bake
  (:use cake.project)
  (:require clojure.main            
            [cake.swank :as swank]
            [cake.server :as server])
  (:import [java.io FileOutputStream PrintStream PrintWriter]))

(def *project* nil)
(def *opts*    nil)

(defmacro defproject "Just save project hash in bake."
  [name version & args]
  (let [opts (apply hash-map args)]
    `(do (alter-var-root #'*project* (fn [_#] (create-project '~name ~version '~opts))))))

(defmacro deftask "Just ignore deftask calls in bake."
  [name & body])

(defn quit []
  (if (= 0 (swank/num-connections))
    (server/quit)
    (println "refusing to quit because there are active swank connections")))

(defn project-eval [[ns ns-forms opts body]]
  (binding [*opts* opts]
    (server/eval-multi `[(~'ns ~ns (:use ~'[bake :only [*project* *opts*]]) ~@ns-forms) ~body])))

(defn start-server [port]
  (init "project.clj")
  (server/redirect-to-log ".cake/project.log")
  (try (doseq [ns (:require *project*)]
         (require ns))
       (eval (:startup *project*))
       (catch Exception e
         (server/print-stacktrace e)))
  (server/create port project-eval :quit quit)
  (when-let [opts (swank/config)]
    (when-not (= false (:auto-start opts))
      (swank/start opts)))
  nil)