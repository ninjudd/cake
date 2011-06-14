(ns bake.notify
  (:use [cake :only [*config* *current-task*]]
        [clojure.java.shell :only [sh]]))

(defn notify [message]
  (print message)
  (flush)
  (when-not (= "true" (get *config* "notifications.disable"))
    (try (sh "growlnotify"
             "-s" (str "cake " *current-task*)
             :in (str message))
         (catch java.io.IOException e))))
