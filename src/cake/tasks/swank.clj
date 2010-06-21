(ns cake.tasks.swank
  (:use cake [cake.tasks.dependencies :only [fetch-deps]])
  (:require [cake.swank :as swank]))

(defn swank-opts [project opts]
  (let [opt    (comp first opts)
        config (merge swank/defaults (:swank project))
        host   (or (opt :host) (opt :h) (:host config))
        port   (or (opt :port) (opt :p) (:port config))
        port   (if (string? port) (Integer/parseInt port) port)]
    {:host host :port port}))

(defn existing-swank-dep? [project]
  (let [swank? #(.matches (name (first %)) "swank-clojure")]
    (or (some swank? (:dependencies project))
        (some swank? (:dev-dependencies project)))))

(deftask deps => swank-deps)
(deftask swank-deps
  (when-let [swank (:swank project)]
    (when-not (existing-swank-dep? project)
      (fetch-deps [(:library swank)] (file "lib/dev")))))

(deftask swank
  "Report status of swank server and start it if not running."
  (bake (:require [cake.swank :as swank])
        [opts (swank-opts project opts)]
        (if (not (swank/installed?))
          (do (println "swank-clojure is not in your library path.")
              (println "add swank-clojure as a dependency in project.clj or touch .cake/swank to enable"))
          (if (swank/running?)
            (let [num (swank/num-connections), s (if (= 1 num) "" "s")]
              (println (format "swank currently running on port %d with %d active connection%s" (swank/port) num s)))
            (if (swank/start opts)
              (println "started swank-clojure server on port" (:port opts))
              (println "unable to start swank-clojure server, port already in use"))))))
