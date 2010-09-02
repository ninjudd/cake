(ns bake.swank
  (:use cake)
  (:import [java.io File StringWriter PrintWriter]))

(def current-port (atom nil))
(defn running? [] (not (nil? @current-port)))

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
          (start-repl port :host host))
        (if (.contains (.toString writer) "java.net.BindException")
          false
          (compare-and-set! current-port nil port)))))
  (do
    (defn installed? [] false)
    (defn num-connections [] 0)
    (defn start [opts] nil)))

