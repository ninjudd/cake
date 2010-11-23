(ns bake.swank
  (:use cake
        [bake.core :only [with-context current-context]]
        [cake.project :only [classloader bake]]
        [cake.utils.useful :only [if-ns]])
  (:import [java.io File StringWriter PrintWriter]))

(def current-port (atom nil))
(defn running? [] (not (nil? @current-port)))

(if-ns (:use [swank.core.server :only [start-swank-socket-server!]]
             [swank.util.net.sockets :only [make-server-socket]])
  (do
    (defn installed? [] true)
    (defn start [host]
      (let [[host port] (if (.contains host ":") (.split host ":") ["localhost" host])
            port        (Integer. port)
            writer      (StringWriter.)]
        ;; wrap all swank threads in a with-context binding
        (alter-var-root #'swank.util.concurrent.thread/start-thread
          (fn [start-thread]
            (fn [f] (start-thread #(with-context (current-context) (f))))))
        (binding [*out* writer
                  *err* (PrintWriter. writer)]
          (start-swank-socket-server!
           (make-server-socket {:port port :host host})
           (fn [socket]
             (bake (:require swank.swank swank.core.server)
                   [socket socket]
                   (let [socket-serve     (ns-resolve 'swank.core.server 'socket-serve)
                         connection-serve (ns-resolve 'swank.swank 'connection-serve)
                         opts {:encoding (or (System/getProperty "swank.encoding") "iso-latin-1-unix")}]
                     (spit "/tmp/swank" (prn-str socket socket-serve connection-serve))
                     (socket-serve connection-serve socket opts))
                   nil))
           {}))
        (if (.contains (.toString writer) "java.net.BindException")
          false
          (compare-and-set! current-port nil port)))))
  (do
    (defn installed? [] false)
    (defn start [opts] nil)))