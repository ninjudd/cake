(ns bake.io
  (:use cake)
  (:import [java.io FileInputStream FileOutputStream BufferedOutputStream PrintStream PrintWriter
                    InputStreamReader OutputStreamWriter]
           [clojure.lang Atom LineNumberingPushbackReader]))

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