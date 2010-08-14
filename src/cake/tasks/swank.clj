(ns cake.tasks.swank
  (:use cake cake.core [cake.tasks.dependencies :only [fetch-deps]])
  (:require [cake.swank :as swank]))

(deftask swank
  "Report status of swank server and start it if not running."
  (bake (:require [cake.swank :as swank]) []
        (if (not (swank/installed?))
          (do (println "swank-clojure is not in your library path.")
              (println "add 'swank = true' or 'swank.auto-start = localhost:4005' to .cake/config to enable"))
          (if (swank/running?)
            (let [num (swank/num-connections), s (if (= 1 num) "" "s")]
              (println
               (format "swank currently running on port %d with %d active connection%s" @swank/current-port num s)))
            (if (swank/start (or (first (:swank *opts*)) "localhost:4005"))
              (println "started swank-clojure server on port" @swank/current-port)
              (println "unable to start swank-clojure server, port already in use"))))))
