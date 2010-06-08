(ns cake.server
  (:require [clojure.contrib.server-socket :as server-socket])
  (:import [java.io File PrintStream InputStreamReader OutputStreamWriter PrintWriter OutputStream]
           [clojure.lang LineNumberingPushbackReader]
           [java.net InetAddress]))

(def *ins*  nil)
(def *outs* nil)

(def servers (atom []))

(defn num-connections []
  (reduce + (map #(count @(:connections %)) @servers)))

(defn quit? [command]
  (or (= :force-quit command)
      (and (= :quit command) (>= 1 (num-connections)))))

(defn- process-command [command]
  (cond (= :validate command)
        (println
         (try (read)
              "valid"
              (catch clojure.lang.LispReader$ReaderException e
                (if (.contains (.getMessage e) "EOF")
                  "incomplete"
                  "invalid"))))
        (quit? command)
        (do (println "true")
            (System/exit 0))))

(defn- create-server* [port f]
  (server-socket/create-server port
    (fn [ins outs]
      (binding [*in*   (LineNumberingPushbackReader. (InputStreamReader. ins))
                *out*  (OutputStreamWriter. outs)
                *err*  (PrintWriter. #^OutputStream outs true)
                *ins*  ins
                *outs* outs]
        (let [form (read)]
          (if (keyword? form)
            (process-command form)
            (try (f form)
                 (catch Exception e
                   (.printStackTrace e (PrintStream. outs))))))))
    0 (InetAddress/getByName "localhost")))

(defn create-server [port f]
  (swap! servers conj (create-server* port f)))
