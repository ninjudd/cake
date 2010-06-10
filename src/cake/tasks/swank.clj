(ns cake.tasks.swank
  (:use cake))

(deftask swank
  "Report status of swank-clojure server and start it if not running."
  "If installed, the swank-server is started automatically when cake starts, so this task is
   primarily for ensuring it is running."
  (bake (:require [bake.swank :as swank]) []
        (if (not (swank/installed?))
          (do (println "swank-clojure not installed.")
              (println "add swank-clojure \"1.2.1\" as a dependency or dev-dependency in project.clj to enable"))
          (if (swank/running?)
            (let [num (swank/num-connections), s (if (= 1 num) "" "s")]
              (println (format "swank currently running on port %d with %d active connection%s" @swank/port num s)))
            (if (swank/start)
              (println "started swank-clojure server on port" @swank/port)
              (println "unable to start swank-clojure server, port already in use"))))))
