(ns bake.swank
  (:import [java.io StringWriter PrintWriter]))

(def default-port 4005)
(defonce port (atom nil))
(defn running? [] (not (nil? @port)))

(defmacro if-ns [ns-reference then-form else-form]
  "Try to load a namespace reference. If sucessful, evaluate then-form otherwise evaluate else-form."
  `(try (ns ~(.getName *ns*) ~ns-reference)
        (eval '~then-form)
        (catch java.io.FileNotFoundException e#
          (eval '~else-form))))

(if-ns (:use [swank.swank :only [start-repl]]
             [swank.core.server :only [*connections*]])
  (do
    (defn installed? [] true)
    (defn num-connections [] (count @*connections*))
    (defn start []
      (let [writer (StringWriter.)]
        (binding [*out* writer
                  *err* (PrintWriter. writer)]
          (start-repl default-port)
          (when-not (.contains (.toString writer) "java.net.BindException")
            (compare-and-set! port nil default-port)
            true)))))
  (do
    (defn installed? [] false)
    (defn num-connections [] 0)
    (defn start [] nil)))

