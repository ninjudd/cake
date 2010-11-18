(ns bake.io
  (:use cake)
  (:import [java.io File FileInputStream FileOutputStream BufferedOutputStream PrintStream PrintWriter
                    InputStreamReader OutputStreamWriter]
           [clojure.lang Atom LineNumberingPushbackReader]))

(defmacro multi-outstream [var]
  (letfn [(outs [val] (if (instance? Atom val) (first @val) val))]
    `(PrintStream.
      (proxy [BufferedOutputStream] [nil]
        (write
          ([b#]           (.write (~outs ~var) b#))
          ([b# off# len#] (.write (~outs ~var) b# off# len#)))
        (flush [] (.flush (~outs ~var)))))))

(defn default-outstream-push [outs default]
  (swap! outs conj default))

(defn default-outstream-pop [outs default]
  (doall (swap! outs (partial remove #(= default %)))))

(defmacro with-streams [ins outs & forms]
  `(do (default-outstream-push *outs* ~outs)
       (default-outstream-push *errs* ~outs)
       (try
         (binding [*in*   (LineNumberingPushbackReader. (InputStreamReader. ~ins))
                   *out*  (OutputStreamWriter. ~outs)
                   *err*  (PrintWriter. #^OutputStream ~outs true)
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