(ns bake.core
  (:use cake
        [cake.utils.useful :only [merge-in into-map]])
  (:require clojure.main            
            cake.project
            [bake.swank :as swank]
            [cake.server :as server])
  (:import [java.io FileOutputStream PrintStream PrintWriter]))

(defmacro defproject "Just save project hash in bake."
  [name version & opts]
  (let [opts (into-map opts)]
    `(do (alter-var-root #'*project* (fn [_#] (cake.project/create '~name ~version '~opts))))))

(defmacro defcontext [name & opts]
  (let [opts (into-map opts)]
    `(alter-var-root #'*context* merge-in {'~name ~opts})))

(defmacro deftask "Just ignore deftask calls in bake."
  [name & body])

(defn quit []
  (if (= 0 (swank/num-connections))
    (server/quit)
    (println "refusing to quit because there are active swank connections")))

(defn project-eval [[ns ns-forms body]]
  (let [result (server/eval-multi `[(~'ns ~ns (:use ~'cake) ~@ns-forms) ~body])]
    (println ::result)
    (println (pr-str result))))

(defn start-server [port]
  (in-ns 'bake.core)
  (cake.project/load-files)
  (server/init-multi-out ".cake/project.log")
  (try (doseq [ns (:require *project*)]
         (require ns))
       (eval (:startup *project*))
       (catch Exception e
         (server/print-stacktrace e)))
  (when-let [auto-start (*config* "swank.auto-start")]    
    (swank/start auto-start))
  (server/create port project-eval :quit quit)
  nil)