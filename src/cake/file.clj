(ns cake.file
  (:use cake
        [useful :only [into-map]]
        [clojure.string :only [join]]
        uncle.core)
  (:import [org.apache.tools.ant.taskdefs Copy Move Touch Delete Mkdir]
           [java.io File]))

(defn- expand-path [path]
  (let [root (or (first path) "")]
    (cond (instance? File root)  (cons (.getPath root) (rest path))
          (.startsWith root "/") path
          (.startsWith root "~") (cons (.replace root "~" (System/getProperty "user.home")) (rest path))
          :else                  (cons *root* path))))

(defn- substitute-context [path]
  (if-let [context (:context *project*)]
    (.replace (str path) "+context+" (name context))
    path))

(defn file-name [& path]
  (join (File/separator)
        (map substitute-context (expand-path path))))

(defn file
  "Create a File object from a string or seq"
  [& path]
  (File. (apply file-name path)))

(defn global-file [& path]
  (apply file *global-root* path))

(defmacro with-root [root & forms]
  `(binding [*root* ~root]
     ~@forms))

#_(use 'uncle.core)

(defn cp [from to & opts]
  (ant Copy
       (into-map opts :file from :tofile to)
       execute))

(defn mv [from to & opts]
  (ant Move
       (into-map opts :file from :tofile to)
       execute))

(defn touch [file & opts]
  (ant Touch
       (into-map opts :file file)
       execute))

(defn rm [file & opts]
  (ant Delete
       (into-map opts :file file)
       execute))

(defn rmdir [file & opts]
  (ant Delete
       (into-map opts :dir file)
       execute))

(defn mkdir [file & opts]
  (ant Mkdir
       (into-map opts :dir file)
       execute))

(defn newer? [& args]
  (apply > (for [arg args]
             (if (number? arg)
               arg
               (.lastModified (if (string? arg) (file arg) arg))))))