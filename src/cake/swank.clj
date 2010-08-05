(ns cake.swank
  (:use [cake.project :only [*config*]])
  (:import [java.io File StringWriter PrintWriter]))

(def *port* nil)
(defn running? [] (not (nil? *port*)))

(defmacro if-ns [ns-reference then-form else-form]
  "Try to load a namespace reference. If sucessful, evaluate then-form otherwise evaluate else-form."
  `(try (ns ~(.getName *ns*) ~ns-reference)
        (eval '~then-form)
        (catch Exception e#
          (when (not (instance? java.io.FileNotFoundException e#))
            (println "Error loading swank:" (.getMessage e#)))
          (eval '~else-form))))

(if-ns (:use [swank.swank :only [start-repl]]
             [swank.core.server :only [*connections*]])
  (do
    (defn installed? [] true)
    (defn num-connections [] (count @*connections*))
    (defn start [host]
      (let [[host port] (if (.contains host ":") (.split host ":") ["localhost" host])
            port        (Integer/parseInt port)
            writer      (StringWriter.)]
        (binding [*out* writer
                  *err* (PrintWriter. writer)]
          (start-repl port :host host)
          (when-not (.contains (.toString writer) "java.net.BindException")
            (alter-var-root #'*port* (fn [_] port))
            true)))))
  (do
    (defn installed? [] false)
    (defn num-connections [] 0)
    (defn start [opts] nil)))

