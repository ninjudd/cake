(ns cake.tasks.swank
  (:use cake cake.core
        [cake.utils.useful :only [if-ns]])
  (:import [java.io StringWriter PrintWriter]))

(def current-port (atom nil))

(defn- serve-swank
  "Run swank connection thread in the project classloader."
  [socket]
  (bake (:require swank.swank swank.core.server)
        [socket socket]
        (let [socket-serve     (ns-resolve 'swank.core.server 'socket-serve)
              connection-serve (ns-resolve 'swank.swank 'connection-serve)
              opts {:encoding (or (System/getProperty "swank.encoding") "iso-latin-1-unix")}]
          (spit "/tmp/swank" (prn-str socket socket-serve connection-serve))
          (socket-serve connection-serve socket opts))))

(defn- wrap-swank-context!
  "Wrap all swank threads in a with-context binding." []
  (bake (:use [swank.util.concurrent.thread :only [start-thread]]
              [bake.core :only [with-context current-context]]) []
        (alter-var-root
         #'start-thread
         (fn [start-thread]
           (fn [f] (start-thread #(with-context (current-context) (f))))))))

(if-ns (:use [swank.core.server :only [start-swank-socket-server!]]
             [swank.util.net.sockets :only [make-server-socket]])

  (defn start-swank [host]
    (wrap-swank-context!)
    (let [[host port] (if (.contains host ":") (.split host ":") ["localhost" host])
          out (with-out-str
                (start-swank-socket-server!
                 (make-server-socket {:port (Integer. port) :host host})
                 serve-swank {}))]
      (if (.contains out "java.net.BindException")
        (println "unable to start swank-clojure server, port already in use")
        (do (compare-and-set! current-port nil port)
            (println "started swank-clojure server on port" @current-port)))))

  (defn start-swank [host]
    (println "error loading swank-clojure.")
    (println "see http://clojure-cake.org/swank for installation instructions")))

(deftask swank
  "Report status of swank server and start it if not running."
  {[host] :swank}
  (if @current-port
    (println "swank currently running on port" @current-port)
    (start-swank (or host "localhost:4005"))))
