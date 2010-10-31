(ns bake.swank
  (:use cake
        [cake.project :only [with-context current-context]]
        [cake.utils.useful :only [if-ns]])
  (:import [java.io File StringWriter PrintWriter]))

(def current-port (atom nil))
(defn running? [] (not (nil? @current-port)))

(if-ns (:require [swank.swank :as swank]
                 [swank.core.server :as swank.server])
  (do
    (defn installed? [] true)
    (defn num-connections []
      (let [connections (or (ns-resolve 'swank.server '*connections*)
                            (ns-resolve 'swank.server 'connections))]
        (count @connections)))
    (defn start [host]
      (let [[host port] (if (.contains host ":") (.split host ":") ["localhost" host])
            port        (Integer/parseInt port)
            writer      (StringWriter.)]
        ;; wrap all swank threads in a with-context binding
        (alter-var-root #'swank.util.concurrent.thread/start-thread
          (fn [start-thread]
            (fn [f] (start-thread #(with-context (current-context) (f))))))
        (binding [*out* writer
                  *err* (PrintWriter. writer)]
          (swank/start-repl port :host host))
        (if (.contains (.toString writer) "java.net.BindException")
          false
          (compare-and-set! current-port nil port)))))
  (do
    (defn installed? [] false)
    (defn num-connections [] 0)
    (defn start [opts] nil)))
