(ns cake.tasks.swank
  (:use cake cake.core)
  (:require [bake.swank :as swank]))

(deftask swank
  "Report status of swank server and start it if not running."
  (if (not (swank/installed?))
    (do (println "swank-clojure is not in your library path.")
        (println "add swank-clojure as a dev-dependency in ~/.cake/project.clj to enable"))
    (if (swank/running?)      
      (println "swank currently running on port" @swank/current-port)
      (if (swank/start (or (first (:swank *opts*)) "localhost:4005"))
        (println "started swank-clojure server on port" @swank/current-port)
        (println "unable to start swank-clojure server, port already in use")))))
