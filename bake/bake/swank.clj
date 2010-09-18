(ns bake.swank
  (:use cake
        [useful :only [if-ns]])
  (:import [java.io File StringWriter PrintWriter]))

(def current-port (atom nil))
(defn running? [] (not (nil? @current-port)))

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

