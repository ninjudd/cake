(ns cake.server
  (:use [cake.contrib.find-namespaces :only [read-file-ns-decl]])
  (:require [cake.contrib.server-socket :as server-socket]
            complete)
  (:import [java.io File PrintStream InputStreamReader OutputStreamWriter PrintWriter OutputStream]
           [clojure.lang LineNumberingPushbackReader]
           [java.net InetAddress]))

(def *ins*  nil)
(def *outs* nil)

(defonce servers (atom []))

(defn num-connections []
  (reduce + (map #(count @(:connections %)) @servers)))

(defn print-stacktrace [e]
  (.printStackTrace e (PrintStream. *outs*)))

(defn validate-form []
  (println
   (try (read)
        "valid"
        (catch clojure.lang.LispReader$ReaderException e
          (if (.contains (.getMessage e) "EOF")
            "incomplete"
            "invalid")))))

(defn completions []
  (let [[prefix ns] (read)]
    (doseq [completion (complete/completions prefix ns)]
      (println completion))))

(defn reload-files []
  (let [files (read)]
    (doseq [file files]
      (if (not (.endsWith file ".clj"))
        (println "cannot reload non-clojure file:" file)
        (if-let [ns (second (read-file-ns-decl (java.io.File. file)))]
          (when (find-ns ns) ;; don't reload namespaces that aren't already loaded
            (try (load-file file)
                 (catch Exception e
                   (print-stacktrace e))))
          (println "cannot reload file without namespace declaration:" file))))))

(defn exit []
  (System/exit 0))

(defn quit []
  (if (>= 1 (num-connections)) ;; add one for the current connection
    (exit)
    (println "refusing to quit because there are active connections")))

(defn repl []
  (let [marker (read)]
    (clojure.main/repl
     :init   #(in-ns 'user)
     :prompt #(println (str marker (ns-name *ns*))))))

(def default-commands
  {:validate    validate-form
   :completions completions
   :reload      reload-files
   :force-quit  exit
   :quit        quit
   :repl        repl})

(defn fatal? [e]
  (and (instance? clojure.lang.Compiler$CompilerException e)
       (instance? UnsatisfiedLinkError (.getCause e))))

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
                (print-stacktrace e)
                (when (fatal? e) (System/exit 1)))))))
      0 (InetAddress/getByName "localhost"))))

(defn create [port f & commands]
  (swap! servers conj (create* port f commands)))
