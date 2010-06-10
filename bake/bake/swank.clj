(ns bake.swank
  (:use bake.utils))

(def port 4005)

(defmacro with-connection [sym form]
  `(when-let [~sym (try (java.net.Socket. "localhost" port) (catch java.net.ConnectException e#))]
     (Thread/sleep 100)
     (let [result# ~form]
       (.close ~sym)
       (Thread/sleep 100)
       result#)))

(if-ns (:use [swank.swank :only [start-repl]]
             [swank.core.server :only [*connections*]])
  (do
    (defn installed? [] true)
    (defn num-connections [] (count @*connections*))
    (defn start [] (start-repl port))
    (defn running? [] (with-connection socket (< 0 (num-connections)))))
  (do
    (defn installed? [] false)
    (defn num-connections [] 0)
    (defn start [] nil)
    (defn running? [] false)))

