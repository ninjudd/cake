(ns cake.tasks.swank
  (:use cake cake.core
        [cake.utils.useful :only [if-ns]]
        [bake.core :only [current-context]])
  (:import [java.io StringWriter PrintWriter]))

(def current-port (atom nil))

(defn- serve-swank [context]
  "Run swank connection thread in the project classloader."
  (fn [socket]
    (bake (:use [bake.core :only [set-context!]])
          (:require swank.swank swank.core.server)
          [socket socket, context context]
          (let [socket-serve     (ns-resolve 'swank.core.server 'socket-serve)
                connection-serve (ns-resolve 'swank.swank 'connection-serve)
                opts {:encoding (or (System/getProperty "swank.encoding") "iso-latin-1-unix")}]
            (eval (:swank-init *project*))
            (set-context! context)
            (socket-serve connection-serve socket opts)))))

(if-ns (:use [swank.core.server :only [start-swank-socket-server!]]
             [swank.util.net.sockets :only [make-server-socket]])

  (defn start-swank [host]
    (let [[host port] (if (.contains host ":") (.split host ":") ["localhost" host])
          out (with-out-str
                (start-swank-socket-server!
                 (make-server-socket {:port (Integer. port) :host host})
                 (serve-swank (current-context)) {}))]
      (if (.contains out "java.net.BindException")
        (println "unable to start swank-clojure server, port already in use")
        (do (compare-and-set! current-port nil port)
            (println "started swank-clojure server on port" @current-port)))))

  (defn start-swank [host]
    (println "error loading swank-clojure.")
    (println "see http://clojure-cake.org/swank for installation instructions")))

(deftask swank #{compile}
  "Report status of swank server and start it if not running."
  {[host] :swank}
  (if @current-port
    (println "swank currently running on port" @current-port)
    (start-swank (or host "localhost:4005"))))
