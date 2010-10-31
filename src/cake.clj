(ns cake
  (:import [java.io File FileInputStream]
           [java.util Properties]))

(def project-root nil)

(def ^:dynamic *current-task* nil)
(def ^:dynamic *project*      nil)
(def ^:dynamic *context*      nil)
(def ^:dynamic *script*       nil)
(def ^:dynamic *opts*         nil)
(def ^:dynamic *pwd*          nil)
(def ^:dynamic *env*          nil)
(def ^:dynamic *vars*         nil)
(def ^:dynamic *File*         nil)
(def ^:dynamic *root* (System/getProperty "cake.project"))

(def ^:dynamic *ins*  nil)
(def ^:dynamic *outs* nil)
(def ^:dynamic *errs* nil)

(defn read-config [file]
  (if (.exists file)
    (with-open [f (FileInputStream. file)]
      (into {} (doto (Properties.) (.load f))))
    {}))

(def ^:dynamic *config*
  (merge (read-config (File. (System/getProperty "user.home") ".cake/config"))
         (read-config (File. ".cake/config"))))
