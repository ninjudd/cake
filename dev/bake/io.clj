(ns bake.io
  (:use cake
        [clojure.java.io :only [copy writer]])
  (:import (java.io FileInputStream FileOutputStream BufferedOutputStream
                    PrintStream PrintWriter InputStreamReader OutputStreamWriter)
           (clojure.lang LineNumberingPushbackReader)))

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