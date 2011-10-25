(ns cake.tasks.swank
  (:use cake cake.core
        [cake.utils :only [keepalive!]]
        [useful.utils :only [if-ns]]
        [bake.core :only [current-context]])
  (:require [clojure.java.io :as io]
            [clojure.string :as string])
  (:import [java.io StringWriter PrintWriter]
           java.util.jar.JarFile))

(def current-port (atom nil))

(defn- serve-swank [context]
  "Run swank connection thread in the project classloader."
  (fn [socket]
    (keepalive!)
    (bake (:use [bake.core :only [with-context]]
                [bake.repl :only [with-wrapper]])
          (:require swank.swank swank.core.server)
          [socket socket, context context]
          (let [socket-serve     (ns-resolve 'swank.core.server 'socket-serve)
                connection-serve (ns-resolve 'swank.swank 'connection-serve)
                opts {:encoding (or (some #(System/getProperty %)
                                          ["swank.encoding" "file.encoding"])
                                    "UTF-8")}]
            (with-context context
              (with-wrapper
                (socket-serve connection-serve socket opts)))))))

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

(deftask swank #{compile-java}
  "Report status of swank server and start it if not running."
  {[host] :swank}
  (if @current-port
    (println "swank currently running on port" @current-port)
    (start-swank (or host "localhost:4005"))))

;; # jack-in

(defn elisp-payload-files []
  (->> (bake [] (.getResources (.getContextClassLoader (Thread/currentThread)) "swank_elisp_payloads.clj"))
       enumeration-seq
       (map (comp read-string slurp))
       (apply concat)
       set))

(defn hex-digest [file]
  (bake
   (:require [clojure.java.io :as io])
   (:import java.security.MessageDigest)
   [file file]
   (format "%x" (BigInteger. 1 (.digest (MessageDigest/getInstance "SHA1")
                                        (-> file io/resource slurp .getBytes))))))

(defn loader [resource]
  (let [feature (second (re-find #".*/(.*?).el$" resource))
        checksum (subs (hex-digest resource) 0 8)
        basename (format "%s/.emacs.d/swank/%s-%s"
                         (System/getProperty "user.home")
                         feature checksum)
        elisp (str basename ".el")
        bytecode (str basename ".elc")
        elisp-file (io/file elisp)]
    (when-not (.exists elisp-file)
      (.mkdirs (.getParentFile elisp-file))
      (with-open [r (.openStream (io/resource resource))]
        (io/copy r elisp-file))
      (with-open [w (io/writer elisp-file :append true)]
        (.write w (format "\n(provide '%s-%s)\n" feature checksum))))
    (format "(when (not (featurep '%s-%s))
               (if (file-readable-p \"%s\")
                 (load-file \"%s\")
               (byte-compile-file \"%s\" t)))"
            feature checksum bytecode bytecode elisp)))

(defn payload-loaders []
  (for [file (elisp-payload-files)]
    (loader file)))

(deftask jack-in
 "Jack in to a Clojure SLIME session from Emacs."
 "This task is intended to be launched from Emacs using M-x clojure-jack-in,
  which is part of the clojure-mode library."
 {[port] :jack-in}
 (println ";;; Bootstrapping bundled version of SLIME; please wait...\n\n")
 (println (string/join "\n" (payload-loaders)))
 (println "(sleep-for 0.1)") ; TODO: remove
 (println "(run-hooks 'slime-load-hook) ; on port" port)
 (bake (:require swank.swank swank.commands.basic)
       [port port]
       (swank.swank/start-server :host "localhost"
                                 :port (if (and port (string? port))
                                         (Integer/parseInt port)
                                         4005)
                                 :repl-out-root true
                                 :block true
                                 :message "\";;; proceed to jack in\"")))

