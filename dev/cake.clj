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
(def ^{:dynamic true} *env*          (into {} (System/getenv)))
(def ^{:dynamic true} *vars*         nil)
(def ^{:dynamic true} *File*         nil)
(def ^{:dynamic true} *root*         (System/getProperty "cake.project"))
(def ^{:dynamic true} *pidfile*      (System/getProperty "cake.pidfile"))
(def ^{:dynamic true} *global-root*  (.getPath (File. (System/getProperty "user.home") ".cake")))
(def ^{:dynamic true} *classloader*  nil)

(def dep-jars (atom nil))
(def test-classloader nil)

(def ^{:dynamic true} *ins*     nil)
(def ^{:dynamic true} *outs*    nil)
(def ^{:dynamic true} *errs*    nil)
(def ^{:dynamic true} *console* nil) ; console out where cake was originally started

(def repl-count (atom 0))

(defn read-config [file]
  (into {} (when (.exists file)
             (with-open [f (FileInputStream. file)]
               (doto (Properties.) (.load f))))))

(def ^{:dynamic true} *config*
  (apply merge (map read-config [(File. (System/getProperty "user.home") ".cake/config")
                                 (File. "cake-config")
                                 (File. ".cake/config")])))
