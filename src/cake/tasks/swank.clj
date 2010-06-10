(ns cake.tasks.swank
  (:use cake))

(deftask swank
  "Report status of swank-clojure server and start it if not running."
  "If installed, the swank-server is started automatically when cake starts, so this task is
   primarily for ensuring it is running."
  (bake (:require bake.swank) []
        (if (not (bake.swank/installed?))
          (do (println "swank-clojure not installed.")
              (println "add swank-clojure \"1.2.1\" as a dependency or dev-dependency in project.clj to enable"))
          (if (bake.swank/running?)
            (println (format "swank currently running on port %d with %d active connections"
                             bake.swank/port (bake.swank/num-connections)))
            (bake.swank/start)))))
