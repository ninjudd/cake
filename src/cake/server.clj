(ns cake.server
  (:use cake
        [cake.project :only [bake]]
        [bake.core :only [with-context current-context]]
        [clojure.main :only [skip-whitespace]]
        [bake.io :only [with-streams]]
        [bake.reload :only [reload]]
        [cake.utils.useful :only [if-ns]])
  (:require [cake.utils.server-socket :as server-socket]
            [bake.complete :as complete]
            [clojure.stacktrace :as stacktrace])
  (:import [java.io File PrintStream InputStreamReader OutputStreamWriter PrintWriter OutputStream
                    FileOutputStream ByteArrayInputStream StringReader FileNotFoundException]
           [clojure.lang LineNumberingPushbackReader LispReader$ReaderException]
           [java.net InetAddress]))

(if-ns (:require [clj-stacktrace.repl :as clj-stacktrace])
  (do
    (defn print-stacktrace [e]
      (if-let [pst-color (get *config* "clj-stacktrace")]
        (do (printf "%s: " (.getName (class e)))
            (clj-stacktrace/pst-on *out* (= "color" pst-color) e))
        (do (stacktrace/print-cause-trace e)
            (flush)))))
  (do
    (defn print-stacktrace [e]
      (stacktrace/print-cause-trace e)
      (flush))))

(defn- read-seq []
  (lazy-seq
   (let [form (read *in* false :cake/EOF)]
     (when-not (= :cake/EOF form)
       (cons form (read-seq))))))

(defn validate-form []
  (println
   (try (doall (read-seq))
        "valid"
        (catch RuntimeException e
          (let [cause (.getCause e)]
            (if (and (instance? LispReader$ReaderException cause) (.contains (.getMessage cause) "EOF"))
              "incomplete"
              "invalid"))))))

(defn completions []
  (let [[prefix ns cake?] (read)]
    (dorun
     (map println
          (if cake?
            (complete/completions prefix ns)
            (bake (:require [bake.complete :as complete])
                  [prefix prefix, ns ns]
                  (complete/completions prefix ns)))))))

(def commands
  {:validate    validate-form
   :completions completions
   :ping        #(println "pong")})

(defn fatal? [e]
  (and (instance? clojure.lang.Compiler$CompilerException e)
       (instance? UnsatisfiedLinkError (.getCause e))))

(defn create [port f]
  (server-socket/create-server port
    (fn [ins outs]
      (with-streams ins outs
        (try
          (let [form (read), vars (read)]
            (clojure.main/with-bindings
              (reload)
              (set! *command-line-args*  (:args vars))
              (set! *warn-on-reflection* (:warn-on-reflection *project*))
              (binding [*vars*    vars
                        *pwd*     (:pwd vars)
                        *env*     (:env vars)
                        *opts*    (:opts vars)
                        *script*  (:script vars)]
                (with-context (current-context)
                  (if (keyword? form)
                    (when-let [command (commands form)]
                      (command))
                    (f form))))))
          (catch Throwable e
            (print-stacktrace e)
            (when (fatal? e) (System/exit 1))))))
    0 (InetAddress/getByName "localhost")))
