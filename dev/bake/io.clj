(ns bake.io
  (:use cake
        [clojure.java.io :only [copy writer]])
  (:import (java.io File FileInputStream FileOutputStream PrintStream PrintWriter InputStreamReader OutputStreamWriter)
           (java.net JarURLConnection)
           (clojure.lang Atom LineNumberingPushbackReader)))

(defn resource-stream [name]
  (if-let [url (.findResource (.getClassLoader clojure.lang.RT) name)]
    (let [conn (.openConnection url)]
      (if (instance? JarURLConnection conn)
        (let [jar (cast JarURLConnection conn)]
          (.getInputStream jar))
        (FileInputStream. (File. (.getFile url)))))))

(defn extract-resource [name dest-dir]
  (if-let [s (resource-stream name)]
    (let [dest (File. dest-dir name)]
      (.mkdirs (.getParentFile dest))
      (copy s dest)
      dest)
    (throw (Exception. (format "unable to find %s on classpath" name)))))

(defmacro with-streams [ins outs & forms]
  `(binding [*in*   (if ~ins  (LineNumberingPushbackReader. (InputStreamReader. ~ins)) *in*)
             *out*  (if ~outs (OutputStreamWriter. ~outs) *out*)
             *err*  (if ~outs (PrintWriter. ~outs true) *err*)
             *outs* ~outs
             *errs* ~outs
             *ins*  ~ins]
     ~@forms))

(defn init-log []
  (alter-var-root #'*console* (constantly *out*))
  (let [outs (PrintStream. (FileOutputStream. ".cake/out.log" true))
        errs (PrintStream. (FileOutputStream. ".cake/err.log" true))]
    (alter-var-root #'*outs* (constantly outs))
    (alter-var-root #'*errs* (constantly errs))
    (alter-var-root #'*out*  (constantly (writer outs)))
    (alter-var-root #'*err*  (constantly (writer errs)))
    (System/setOut outs)
    (System/setErr errs)))

(defn disconnect [& [wait]]
  (let [outs *outs*]
    (future
      (when wait
        (Thread/sleep wait))
      (.close outs))))