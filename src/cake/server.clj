(ns cake.server
  (:use cake
        [cake.project :only [bake]]
        [bake.core :only [with-context current-context]]
        [clojure.main :only [skip-whitespace]]
        [bake.io :only [multi-outstream with-streams]]
        [cake.utils.useful :only [if-ns]])
  (:require bake.repl
            [cake.utils.server-socket :as server-socket]
            [cake.utils.complete :as complete]
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

(defonce num-connections (atom 0))

(defn read-seq []
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
  (let [[prefix ns] (read)]
    (doseq [completion (complete/completions prefix ns)]
      (println completion))))

(defn exit []
  (System/exit 0))

(defn quit []
  (if (= 0 @num-connections)
    (exit)
    (println "warning: refusing to quit because there are active connections")))

(defn repl []
  (if (:cake *opts*)
    (bake.repl/repl (read))
    (bake (:use bake.repl) [marker (read)]        
          (repl marker))))

(defn eval-verbose [form]
  (try (eval form)
       (catch Throwable e
         (println "evaluating form:" (prn-str form))
         (throw e))))

(defn eval-multi
  ([] (eval-multi (doall (read-seq))))
  ([forms]
     (binding [*ns* (the-ns 'user)]
       (last
        (for [form forms]
          (eval-verbose form))))))

(defn eval-filter []
  (let [end (read)]
    (eval-multi
     (for [[line & forms] (read-seq)]
       `(do (-> ~line ~@forms (println))
            (println ~end))))))

(defn run-file []
  (let [script (read)
        script-ns (gensym "script")]
    (try
      (binding [*ns* (create-ns script-ns)]
        (clojure.core/refer-clojure)
        (load-file script))
      (finally (remove-ns script-ns)))))

(def default-commands
  {:validate    validate-form
   :completions completions
   :force-quit  exit
   :quit        quit
   :repl        repl
   :eval        eval-multi
   :filter      eval-filter
   :run         run-file
   :ping        #(println "pong")})

(defn fatal? [e]
  (and (instance? clojure.lang.Compiler$CompilerException e)
       (instance? UnsatisfiedLinkError (.getCause e))))

(defn create [port f & commands]
  (let [commands (apply hash-map commands)]
    (server-socket/create-server port
      (fn [ins outs]
        (with-streams ins outs
          (try
            (let [form (read), vars (read)]
              (clojure.main/with-bindings
                (set! *command-line-args*  (:args vars))
                (set! *warn-on-reflection* (:warn-on-reflection *project*))
                (binding [*vars*    vars
                          *pwd*     (:pwd vars)
                          *env*     (:env vars)
                          *opts*    (:opts vars)
                          *script*  (:script vars)]
                  (with-context (current-context)
                    (if (keyword? form)
                      (when-let [command (or (commands form) (default-commands form))]
                        (command))
                      (f form))))))
            (catch Throwable e
              (print-stacktrace e)
              (when (fatal? e) (System/exit 1))))))
      0 (InetAddress/getByName "localhost"))))
