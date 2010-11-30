(ns cake.main
  (:use cake
        [cake.task :only [run-task run?]]
        [cake.file :only [file]]
        [cake.ant  :only [in-project]]
        [clojure.contrib.condition :only [handler-case *condition*]]
        [cake.utils :only [*readline-marker*]]
        [bake.reload :only [reloader]]
        [bake.io :only [init-multi-out]])
  (:require [cake.tasks default global]
            [cake.project :as project]
            [cake.server :as server])
  (:import (java.lang ClassLoader)
           (java.io File)))

(defn process-command [[task readline-marker]]
  (binding [*readline-marker* readline-marker]
    (in-project
     (doseq [dir ["lib" "classes" "build"]]
       (.mkdirs (file dir)))
     (handler-case :type
       (run-task (symbol (name task)))
       (handle :abort-task
         (println (name task) "aborted:" (:message *condition*)))))))

(defn start-server [port]
  (in-project
   (let [classpath (for [url (.getURLs (ClassLoader/getSystemClassLoader))]
                     (File. (.getFile url)))
         project-files (project/files ["project.clj" "context.clj" "tasks.clj" "dev.clj"] ["tasks.clj" "dev.clj"])]
     (ns cake.user
       (:use cake.core))
     (doseq [file project-files :when (.exists file)]
       (load-file (.getPath file)))
     (when-not *project* (require '[cake.tasks help new]))
     (init-multi-out ".cake/cake.log")
     (server/create port process-command
       :reload (reloader classpath project-files (File. "lib/dev")))
     nil)))
