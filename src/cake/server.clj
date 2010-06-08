(ns cake.server
  (:use [clojure.useful :only [trap]])
  (:require [clojure.contrib.server-socket :as server-socket])
  (:import [java.io File PrintStream InputStreamReader OutputStreamWriter]
           [clojure.lang LineNumberingPushbackReader]
           [java.net InetAddress]))

(def *ins*  nil)
(def *outs* nil)

(defn- create-server* [port f]
  (server-socket/create-server port
    (fn [ins outs]
      (binding [*in*   (LineNumberingPushbackReader. (InputStreamReader. ins))
                *out*  (OutputStreamWriter. outs)
                *err*  *out*
                *ins*  ins
                *outs* outs]
        (let [form (read)]
          (try (f form)
               (catch Exception e
                 (.printStackTrace e (PrintStream. outs)))))))
    0 (InetAddress/getByName "localhost")))

(defn create-server [port f pidfile]
  (let [server (create-server* port f)]
    (trap "HUP"
      (fn [signal]
        (when (empty? @(:connections server))
          (do (.delete (File. pidfile))
              (System/exit 0)))))
    server))
