(ns cake.main
  (:use cake
        [cake.deps :only [fetch-deps!]]
        [cake.task :only [run-task run?]]
        [cake.file :only [file global-file]]
        [cake.utils :only [*readline-marker*]]
        [cake.project :only [reload  reset-classloaders! reset-test-classloader! append-dev-dependencies!]]
        [cake.tasks.swank :only [start-swank]]
        [useful.java :only [on-shutdown]]
        [bake.io :only [init-log]]
        [bake.core :only [debug?]]
        [bake.reload :only [reload-project-files]]
        [uncle.core :only [in-project]]
        [clojure.contrib.condition :only [handler-case *condition*]])
  (:require [cake.tasks default global]
            [cake.server :as server]))

(defn process-command [[task readline-marker]]
  (binding [*readline-marker* readline-marker]
    (in-project {:outs *outs* :verbose (debug?) :root *root*}
      (doseq [dir ["lib" "classes" "build"]]
        (.mkdirs (file dir)))
      (handler-case :type
        (if (or (:r *opts*) (:reset *opts*))
          (if (:test *opts*)
            (reset-test-classloader!)
            (reset-classloaders!))
          (reload))
        (run-task (symbol (name task)))
        (handle :abort-task
          (println (name task) "aborted:" (:message *condition*)))))))

(defn start-server []
  (init-log)
  (reload-project-files)
  (eval (:startup *project*))
  (on-shutdown #(eval (:shutdown *project*)))
  (in-project {:outs System/out :verbose (debug?) :root *root* :default-task "startup"}
    (fetch-deps!)
    (append-dev-dependencies!)
    (reset-classloaders!)
    (when-let [autostart (get *config* "swank.autostart")]
      (start-swank autostart))
    (let [server (server/create process-command)
          port   (.getLocalPort (:server-socket server))]
      (spit *pidfile* (str port "\n") :append true)))
  nil)

