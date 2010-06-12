(ns cake.server
  (:use [cake.contrib.find-namespaces :only [read-file-ns-decl]])
  (:require [cake.contrib.server-socket :as server-socket])
  (:import [java.io File PrintStream InputStreamReader OutputStreamWriter PrintWriter OutputStream]
           [clojure.lang LineNumberingPushbackReader]
           [java.net InetAddress]))

(def *ins*  nil)
(def *outs* nil)

(defonce servers (atom []))

(defn num-connections []
  (reduce + (map #(count @(:connections %)) @servers)))

(defn validate-form []
  (println
   (try (read)
        "valid"
        (catch clojure.lang.LispReader$ReaderException e
          (if (.contains (.getMessage e) "EOF")
            "incomplete"
            "invalid")))))

(defn reload-files []
  (let [files (read)]
    (doseq [file files]
      (if (not (.endsWith file ".clj"))
        (println "cannot reload non-clojure file:" file)
        (if-let [ns (second (read-file-ns-decl (java.io.File. file)))]
          (when (find-ns ns) ;; don't reload namespaces that aren't already loaded
            (load-file file)))))))

(defn exit []
  (System/exit 0))

(defn quit []
  (if (>= 1 (num-connections))
    (exit)
    (println "refusing to quit because there are active connections")))

(def default-commands
  {:validate   validate-form
   :reload     reload-files
   :force-quit exit
   :quit       quit})

(defn- create* [port f commands]
  (let [commands (apply hash-map commands)]
    (server-socket/create-server port
      (fn [ins outs]
        (binding [*in*   (LineNumberingPushbackReader. (InputStreamReader. ins))
                  *out*  (OutputStreamWriter. outs)
                  *err*  (PrintWriter. #^OutputStream outs true)
                  *ins*  ins
                  *outs* outs]
          (let [form (read)]
            (try
              (if (keyword? form)
                (when-let [command (or (commands form) (default-commands form))]
                  (command))
                (f form))
              (catch Exception e
                (.printStackTrace e (PrintStream. outs)))))))
      0 (InetAddress/getByName "localhost"))))

(defn create [port f & commands]
  (swap! servers conj (create* port f commands)))
