(ns cake.main
  (:use cake
        [cake.deps :only [fetch-deps!]]
        [cake.task :only [run-task run?]]
        [cake.file :only [file global-file]]
        [cake.utils :only [*readline-marker* keepalive! start-watchdog!]]
        [cake.classloader :only [reload  reset-classloaders! reset-test-classloader! append-plugin-dependencies!]]
        [cake.tasks.swank :only [start-swank]]
        [useful.java :only [on-shutdown]]
        [bake.io :only [init-multi-out]]
        [bake.core :only [debug?]]
        [bake.reload :only [reload-project-files load-files project-files task-files]]
        [uncle.core :only [in-project]]
        [slingshot.core :only [try+]])
  (:require [cake.tasks default global]
            [cake.server :as server]))

(defn process-command [[task readline-marker]]
  (binding [*readline-marker* readline-marker]
    (in-project {:outs *outs* :verbose (debug?) :root *root*}
      (keepalive!)
      (doseq [dir ["lib" "classes" "build"]]
        (.mkdirs (file dir)))
      (try+
        (if (or (:r *opts*) (:reset *opts*))
          (if (:test *opts*)
            (reset-test-classloader!)
            (reset-classloaders!))
          (reload))
        (run-task (symbol (name task)))
        (catch :abort-task e
          (println (name task) "aborted:" (:message e)))))))

(defn start-server []
  (init-multi-out)
  (println (format "[%tc] -- cake server started" (System/currentTimeMillis)))
  (reload-project-files project-files)
  (eval (:startup *project*))
  (on-shutdown #(eval (:shutdown *project*)))
  (in-project {:outs System/out :verbose (debug?) :root *root* :default-task "startup"}
    (binding [*out* *console*]
      (fetch-deps!))
    (append-plugin-dependencies!)
    (reset-classloaders!)
    (when-let [autostart (get *config* "swank.autostart")]
      (start-swank autostart))
    (let [server (server/create process-command)
          port   (.getLocalPort (:server-socket server))]
      (spit *pidfile* (str port "\n") :append true)))
  (load-files task-files)
  (start-watchdog!)
  nil)

