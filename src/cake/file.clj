(ns cake.file
  (:use cake
        [useful.map :only [into-map]]
        [clojure.string :only [join]]
        uncle.core)
  (:import [org.apache.tools.ant.taskdefs Copy Move Touch Delete Chmod Mkdir Replace]
           [java.io File]))

(defn- expand-path [root path]
  (let [root (or root "")]
    (cond (instance? File root)  (cons (.getPath root) path)
          (.startsWith root "/") (cons root path)
          (.startsWith root "~") (cons (.replace root "~" (System/getProperty "user.home")) path)
          :else                  (list* *root* root path))))

(defn- substitute-context [path]
  (if-let [context (:context *project*)]
    (.replace (str path) "+context+" (name context))
    path))

(defn file-name [root & path]
  (join (File/separator)
        (map substitute-context (expand-path root path))))

(defn file
  "Create a File object from a string or seq"
  ([root]
     (if (instance? File root)
       root
       (File. (file-name root))))
  ([root & path]
     (File. (apply file-name root path))))

(defn path-string [& path]
  (.getPath (apply file path)))

(defn parent [& path]
  (.getParentFile (apply file path)))

(defn file-exists? [& path]
  (.exists (apply file path)))

(defn directory? [& path]
  (.isDirectory (apply file path)))

(defn file? [& path]
  (.isFile (apply file path)))

(defn global-file [& path]
  (apply file *global-root* path))

(defmacro with-root [root & forms]
  `(binding [*root* ~root]
     ~@forms))

(defn cp [from to & opts]
  (ant Copy
    (into-map opts :file from :tofile to)))

(defn mv [from to & opts]
  (ant Move
    (into-map opts :file from :tofile to)))

(defn touch [file & opts]
  (ant Touch
    (into-map opts :file file)))

(defn rm [file & opts]
  (ant Delete
    (into-map opts :file file)))

(defn rmdir [file & opts]
  (ant Delete
    (into-map opts :dir file)))

(defn mkdir [file & opts]
  (ant Mkdir
    (into-map opts :dir file)))

(defn chmod [file perm & opts]
  (let [type (if (directory? file) :dir :file)]
    (ant Chmod
      (into-map opts type file :perm perm))))

(defn newer? [& args]
  (apply > (for [arg args]
             (if (number? arg)
               arg
               (.lastModified (if (string? arg) (file arg) arg))))))

(defn older? [& args]
  (apply newer? (reverse args)))

(defn replace-token [file token value]
  (ant Replace {:file file :token token :value value}))