(ns bake.io
  (:use cake)
  (:import [java.io File FileInputStream FileOutputStream BufferedOutputStream PrintStream PrintWriter
                    InputStreamReader OutputStreamWriter]
           [clojure.lang Atom LineNumberingPushbackReader]))

(defn get-outs [val]
  (if (instance? Atom val) (first @val) val))

(defmacro multi-outstream [var]
  `(PrintStream.
    (proxy [BufferedOutputStream] [nil]
      (write
        ([b#]           (.write (~get-outs ~var) b#))
        ([b# off# len#] (.write (~get-outs ~var) b# off# len#)))
      (flush [] (.flush (~get-outs ~var))))))

(defn default-outstream-push [outs default]
  (swap! outs conj default))

(defn default-outstream-pop [outs default]
  (doall (swap! outs (partial remove #(= default %)))))

(defmacro with-streams [ins outs & forms]
  `(do (default-outstream-push *outs* ~outs)
       (default-outstream-push *errs* ~outs)
       (try
         (binding [*in*   (if ~ins (LineNumberingPushbackReader. (InputStreamReader. ~ins)) *in*)
                   *out*  (if ~outs (OutputStreamWriter. ~outs) *out*)
                   *err*  (if ~outs (PrintWriter. #^OutputStream ~outs true) *out*)
                   *outs* ~outs
                   *errs* ~outs
                   *ins*  ~ins]
           ~@forms)
         (finally
          (default-outstream-pop *outs* ~outs)
          (default-outstream-pop *errs* ~outs)))))

(defn init-multi-out []
  (let [outs (multi-outstream *outs*)
        errs (multi-outstream *errs*)
        log  (FileOutputStream. ".cake/cake.log" true)]
    (alter-var-root #'*outs* (fn [_] (atom (list log))))
    (alter-var-root #'*errs* (fn [_] (atom (list log))))
    (alter-var-root #'*out*  (fn [_] (PrintWriter. outs)))
    (alter-var-root #'*err*  (fn [_] (PrintWriter. errs)))
    (System/setOut outs)
    (System/setErr errs)))