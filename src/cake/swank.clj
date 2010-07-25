(ns cake.swank
  (:import [java.io File StringWriter PrintWriter]))

(def defaults {:host "localhost" :port 4005 :library ['swank-clojure "1.2.1"]})

(defn config []
  (let [file (File. ".cake/swank")]
    (when (.exists file)
      (try (let [string (slurp file)]
             (merge defaults
                    (if (empty? string) {} (read-string string))))
           (catch Exception e
             (println "error reading .cake/swank:")
             (println (.getMessage e)))))))

(defonce *port* (atom nil))
(defn port [] @*port*)
(defn running? [] (not (nil? @*port*)))

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
    (defn start [opts]
      (let [port   (:port opts)
            writer (StringWriter.)]
        (binding [*out* writer
                  *err* (PrintWriter. writer)]
          (start-repl port :host (:host opts))
          (when-not (.contains (.toString writer) "java.net.BindException")
            (compare-and-set! *port* nil port)
            true)))))
  (do
    (defn installed? [] false)
    (defn num-connections [] 0)
    (defn start [opts] nil)))

