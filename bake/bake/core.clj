(ns bake.core
  (:use cake
        [cake.reload :only [reloader]]
        [cake.utils.useful :only [merge-in into-map]])
  (:require clojure.main
            [bake.swank :as swank]
            [cake.server :as server]
            [cake.project :as project])
  (:import [java.io File FileOutputStream PrintStream PrintWriter]))

(defmacro defproject "Just save project hash in bake."
  [name version & opts]
  (let [opts (into-map opts)]
    `(do (alter-var-root #'*project*    (fn [_#] (project/create '~name ~version '~opts)))
         (alter-var-root #'project-root (fn [_#] (project/create '~name ~version '~opts))))))

(defmacro defcontext [name & opts]
  (let [opts (into-map opts)]
    `(alter-var-root #'*context* merge-in {'~name '~opts})))

(defmacro deftask "Just ignore deftask calls in bake."
  [name & body])

(defmacro defile "Same as deftask above"
  [name & body])

(defn quit []
  (if (= 0 (swank/num-connections))
    (server/quit)
    (println "refusing to quit because there are active swank connections")))

(defn project-eval [[current-task ns-forms body]]
)

(defn start-server [port]
  (let [project-files (project/files ["project.clj" "context.clj" "dev.clj"] ["dev.clj"])]
    (in-ns 'bake.core)
    (project/load-files project-files)
    (server/init-multi-out ".cake/project.log")
    (try (doseq [ns (:require *project*)]
           (require ns))
         (eval (:startup *project*))
         (catch Exception e
           (server/print-stacktrace e)))
    (when-let [auto-start (get *config* "swank.auto-start")]
      (swank/start auto-start))
    (server/create port project-eval
      :reload (reloader project/classpath project-files (File. "lib"))
      :quit   quit)
    nil))
