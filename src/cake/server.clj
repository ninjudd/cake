(ns cake.server
  (:require [clojure.contrib.server-socket :as server-socket])
  (:import [java.io PrintStream InputStreamReader OutputStreamWriter]
           [clojure.lang LineNumberingPushbackReader]
           [java.net InetAddress]))

(def *ins*  nil)
(def *outs* nil)

(defn create-server [port fun]
  (server-socket/create-server port
    (fn [ins outs]
      (binding [*in*   (LineNumberingPushbackReader. (InputStreamReader. ins))
                *out*  (OutputStreamWriter. outs)
                *err*  *out*
                *ins*  ins
                *outs* outs]
        (let [form (read)]
          (try (fun form)
               (catch Exception e
                 (.printStackTrace e (PrintStream. outs)))))))
    0 (InetAddress/getByName "localhost")))
