(ns bake.io
  (:use cake
        [clojure.java.io :only [copy]])
  (:import (java.io File FileInputStream FileOutputStream BufferedOutputStream PrintStream PrintWriter
                    InputStreamReader OutputStreamWriter)
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

(defmacro with-outstreams [[sym var] & forms]
  `(let [root# (alter-var-root #'~var identity)]
     (when (not= root# ~var)
       (let [~sym root#]
         ~@forms))
     (let [~sym ~var]
       ~@forms)))

(defmacro multi-outstream [var]
  `(PrintStream.
    (proxy [BufferedOutputStream] [nil]
      (write
        ([b#]
           (with-outstreams [outs# ~var]
             (.write outs# b#)))
        ([b# off# len#]
           (with-outstreams [outs# ~var]
             (.write outs# b# off# len#))))

      (flush []
        (with-outstreams [outs# ~var]
          (.flush outs#))))))

(defmacro with-streams [ins outs & forms]
  `(binding [*in*   (if ~ins  (LineNumberingPushbackReader. (InputStreamReader. ~ins)) *in*)
             *out*  (if ~outs (OutputStreamWriter. ~outs) *out*)
             *err*  (if ~outs (PrintWriter. ~outs true) *err*)
             *outs* ~outs
             *errs* ~outs
             *ins*  ~ins]
     ~@forms))

(defn init-multi-out [log-file]
  (let [outs (multi-outstream *outs*)
        errs (multi-outstream *errs*)
        log  (FileOutputStream. log-file true)]
    (alter-var-root #'*outs* (fn [_] log))
    (alter-var-root #'*errs* (fn [_] log))
    (alter-var-root #'*out*  (fn [_] (PrintWriter. outs)))
    (alter-var-root #'*err*  (fn [_] (PrintWriter. errs)))
    (System/setOut outs)
    (System/setErr errs)))

(defn disconnect [& [wait]]
  (let [outs *outs*]
    (future
      (when wait
        (Thread/sleep wait))
      (.close outs))))