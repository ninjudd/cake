(ns cake
  (:import [java.io File FileInputStream]
           [java.util Properties]))

(def *current-task* nil)
(def *project*      nil)
(def *config*       nil)
(def *script*       nil)
(def *opts*         nil)
(def *pwd*          nil)
(def *env*          nil)
(def *vars*         nil)
(def *root* (System/getProperty "cake.project"))

(def *ins*  nil)
(def *outs* nil)

(defn verbose? []
  (boolean (or (:v *opts*) (:verbose *opts*))))

(defn debug? []
  (boolean (or (:d *opts*) (:debug *opts*))))

(defn read-config [file]
  (if (.exists file)
    (with-open [f (FileInputStream. file)]
      (into {} (doto (Properties.) (.load f))))
    {}))

(def *config* (merge (read-config (File. (System/getProperty "user.home") ".cake/config"))
                     (read-config (File. ".cake/config"))))