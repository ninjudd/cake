(ns cake.main
  (:use cake
        [cake.task :only [run-task run?]]
        [cake.file :only [file global-file]]
        [cake.utils :only [*readline-marker*]]
        [cake.project :only [reset-classloaders!]]
        [cake.tasks.swank :only [start-swank]]
        [useful :only [on-shutdown]]
        [bake.core :only [debug?]]
        [bake.io :only [init-multi-out]]
        [bake.reload :only [reload-project-files]]
        [uncle.core :only [in-project]]
        [clojure.contrib.condition :only [handler-case *condition*]])
  (:require [cake.tasks default global]
            [cake.server :as server])
  (:import (java.lang ClassLoader)
           (java.io File)))

(defn process-command [[task readline-marker]]
  (binding [*readline-marker* readline-marker]
    (in-project {:outs *outs* :verbose (debug?) :root *root*}
      (doseq [dir ["lib" "classes" "build"]]
        (.mkdirs (file dir)))
      (handler-case :type
        (run-task (symbol (name task)))
        (handle :abort-task
          (println (name task) "aborted:" (:message *condition*)))))))

(defn start-server [port]
  (reload-project-files)
  (eval (:startup *project*))
  (on-shutdown #(eval (:shutdown *project*)))
  (init-multi-out ".cake/cake.log")
  (in-project {:outs *outs* :verbose (debug?) :root *root*}
    (reset-classloaders!)
    (when-let [autostart (get *config* "swank.autostart")]
      (start-swank autostart))
    (server/create port process-command)))
