(ns bake
  (:use [clojure.main :only [repl]]
        [clojure.contrib.server-socket :only [create-server create-repl-server]])
  (:import [java.io InputStreamReader OutputStream OutputStreamWriter]
           [clojure.lang LineNumberingPushbackReader]
           [java.net InetAddress]))

(defn execute-form [ins outs]
  (binding [*in*  (LineNumberingPushbackReader. (InputStreamReader. ins))
            *out* (OutputStreamWriter. outs)
            *err* *out*]
    (let [form (read)]
      (try (eval form)
           (catch Exception e
             (.printStackTrace e (java.io.PrintStream. outs)))))))

(defn start-server [port]
  (create-server port execute-form 0 (InetAddress/getByName "localhost")))

