(ns cake.server
  (:use cake
        [clojure.main :only [skip-whitespace]]
        [cake.utils.io :only [multi-outstream with-outstream]]
        [cake.utils.find-namespaces :only [read-file-ns-decl]])
  (:require [cake.utils.server-socket :as server-socket]
            [cake.utils.complete :as complete])
  (:import [java.io File PrintStream InputStreamReader OutputStreamWriter PrintWriter OutputStream
                    FileOutputStream ByteArrayInputStream StringReader FileNotFoundException]
           [clojure.lang LineNumberingPushbackReader LispReader$ReaderException]
           [java.net InetAddress]))

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

(defn reload-files []
  (let [files (read)]
    (doseq [file files]
      (if (not (.endsWith file ".clj"))
        (println "reload-failed: cannot reload non-clojure file:" file)
        (if-let [ns (second (read-file-ns-decl (java.io.File. file)))]
          (if (symbol? ns)
            (when (find-ns ns) ;; don't reload namespaces that aren't already loaded
              (try (load-file file)
                   (catch Exception e
                     (print-stacktrace e))))
            (throw (Exception. (format "invalid ns declaration in %s" file))))
          (println "reload-failed: cannot reload file without namespace declaration:" file))))))

(defn exit []
  (System/exit 0))

(defn quit []
  (if (= 0 @num-connections)
    (exit)
    (println "warning: refusing to quit because there are active connections")))

(defn- reset-in []
  (while (.ready *in*) (.read *in*)))

(defn repl []
  (let [marker (read)]
    (try (swap! num-connections inc)
         (clojure.main/repl
          :init   #(ns user)
          :caught #(do (reset-in) (clojure.main/repl-caught %))
          :prompt #(println (str marker (ns-name *ns*))))
         (finally (swap! num-connections dec)))))

(defn eval-verbose [form]
  (try (eval form)
       (catch Throwable e
         (println "evaluating form:" (prn-str form))
         (throw e))))

(defn eval-multi
  ([] (eval-multi (doall (read-seq))))
  ([forms]
     (in-ns 'user)
     (last
      (for [form forms]
        (eval-verbose form)))))

(defn eval-filter []
  (let [end (read)]
    (eval-multi
     (for [[line & forms] (read-seq)]
       `(do (-> ~line ~@forms (println))
            (println ~end))))))

(defn run-file []
  (let [script (read)]
    (load-file script)))

(def default-commands
  {:validate    validate-form
   :completions completions
   :reload      reload-files
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
        (with-outstream [*outs* outs, *errs* outs]
          (binding [*in*  (LineNumberingPushbackReader. (InputStreamReader. ins))
                    *out* (OutputStreamWriter. outs)
                    *err* (PrintWriter. #^OutputStream outs true)
                    *ins* ins]
            (try
              (let [form (read), vars (read)]
                (clojure.main/with-bindings
                  (set! *command-line-args*  (:args vars))
                  (set! *warn-on-reflection* (:warn-on-reflection *project*))
                  (binding [*vars*    vars
                            *pwd*     (:pwd vars)
                            *env*     (:env vars)
                            *opts*    (:opts vars)
                            *script*  (:script vars)
                            *context* (context (get-in vars [:opts :context 0]))]
                    (if (keyword? form)
                      (when-let [command (or (commands form) (default-commands form))]
                        (command))
                      (f form)))))
              (catch Throwable e
                (print-stacktrace e)
                (when (fatal? e) (System/exit 1)))))))
      0 (InetAddress/getByName "localhost"))))

(defn init-multi-out [logfile]
  (let [outs (multi-outstream *outs*)
        errs (multi-outstream *errs*)
        log  (FileOutputStream. logfile true)]
    (alter-var-root #'*outs* (fn [_] (atom (list log))))
    (alter-var-root #'*errs* (fn [_] (atom (list log))))
    (alter-var-root #'*out*  (fn [_] (PrintWriter. outs)))
    (alter-var-root #'*err*  (fn [_] (PrintWriter. errs)))
    (System/setOut outs)
    (System/setErr errs)))
