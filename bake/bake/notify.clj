(ns bake.notify
  (:use [cake :only [*config* *current-task*]]
        [clojure.java.shell :only [sh]]))

(defn notify [message]
  (when-not (= "true" (get *config* "notifications.disable"))
    (try (sh "growlnotify" (str "cake " *current-task*) "-m" message)
         (catch java.io.IOException e))))
