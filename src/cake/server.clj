(ns cake.server
  (:use cake
        [cake.classloader :only [bake]]
        [bake.core :only [with-context current-context print-stacktrace]]
        [bake.io :only [with-streams]]
        [bake.reload :only [reload]]
        [useful.utils :only [if-ns]]
        [clojure.main :only [skip-whitespace]])
  (:require [cake.utils.server-socket :as server-socket]
            [bake.complete :as complete])
  (:import [java.io File PrintStream InputStreamReader OutputStreamWriter PrintWriter OutputStream
                    FileOutputStream ByteArrayInputStream StringReader FileNotFoundException]
           [clojure.lang LineNumberingPushbackReader LispReader$ReaderException]
           [java.net InetAddress]))

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

(defn create [f]
  (server-socket/create-server 0
    (fn [ins outs]
      (with-streams ins outs
        (try
          (let [form (read), vars (read)]
            (clojure.main/with-bindings
              (reload)
              (binding [*vars*    vars
                        *pwd*     (:pwd vars)
                        *env*     (:env vars)
                        *opts*    (:opts vars)
                        *args*    (:args vars)
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
