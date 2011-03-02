(ns cake
  (:import [java.io File FileInputStream]
           [java.util Properties]))

(def ^{:dynamic true} *current-task* nil)
(def ^{:dynamic true} *project-root* (comment project))
(def ^{:dynamic true} *project*      (comment project))
(def ^{:dynamic true} *context*      (comment context))
(def ^{:dynamic true} *script*       nil)
(def ^{:dynamic true} *opts*         nil)
(def ^{:dynamic true} *args*         nil)
(def ^{:dynamic true} *pwd*          nil)
(def ^{:dynamic true} *env*          nil)
(def ^{:dynamic true} *vars*         nil)
(def ^{:dynamic true} *File*         nil)
(def ^{:dynamic true} *root*        (System/getProperty "cake.project"))
(def ^{:dynamic true} *global-root* (.getPath (File. (System/getProperty "user.home") ".cake")))

(def ^{:dynamic true} *ins*  nil)
(def ^{:dynamic true} *outs* nil)
(def ^{:dynamic true} *errs* nil)

(defn read-config [file]
  (if (.exists file)
    (with-open [f (FileInputStream. file)]
      (into {} (doto (Properties.) (.load f))))
    {}))

(def ^{:dynamic true} *config*
  (apply merge (map read-config [(File. (System/getProperty "user.home") ".cake/config")
                                 (File. "cake-config")
                                 (File. ".cake/config")])))
