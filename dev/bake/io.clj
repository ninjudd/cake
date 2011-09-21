(ns bake.io
  (:use cake
        [clojure.java.io :only [copy writer]])
  (:import (java.io File FileInputStream FileOutputStream BufferedOutputStream
                    PrintStream PrintWriter InputStreamReader OutputStreamWriter)
           (java.net JarURLConnection)
           (clojure.lang Atom LineNumberingPushbackReader)))

;; TODO: move to classlojure
(defn resource-stream [classloader name]
  (if-let [url (.findResource classloader name)]
    (let [conn (.openConnection url)]
      (if (instance? JarURLConnection conn)
        (let [jar (cast JarURLConnection conn)]
          (.getInputStream jar))
        (FileInputStream. (File. (.getFile url)))))))

(defn extract-resource [classloader name dest-dir]
  (if-let [s (resource-stream classloader name)]
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

(defmacro multi-outstream [var]
  `(PrintStream.
    (proxy [BufferedOutputStream] [nil]
      (write
        ([b#]
           (.write ~var b#))
        ([b# off# len#]
           (.write ~var b# off# len#)))
      (flush []
        (.flush ~var)))))

(defn init-multi-out []
  (alter-var-root #'*console* (constantly *out*))
  (alter-var-root #'*outs*    (constantly (FileOutputStream. ".cake/out.log" true)))
  (alter-var-root #'*errs*    (constantly (FileOutputStream. ".cake/err.log" true)))
  (let [outs (multi-outstream *outs*)
        errs (multi-outstream *errs*)]
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