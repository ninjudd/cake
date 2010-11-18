(ns cake.file
  (:use cake
        [cake.utils.useful :only [into-map]]
        [clojure.string :only [join]])
  (:import [org.apache.tools.ant.taskdefs Copy Move Touch Delete Mkdir]
           [java.io File]))

(defn expand-path [& path]
  (let [root (or (first path) "")]
    (cond (instance? File root)  (cons (.getPath root) (rest path))
          (.startsWith root "/") path
          (.startsWith root "~") (cons (.replace root "~" (System/getProperty "user.home")) (rest path))
          :else                  (cons *root* path))))

(defn file-name [& path]
  (join (File/separator) (apply expand-path path)))

(defn file
  "Create a File object from a string or seq"
  [& path]
  (File. (apply file-name path)))

(use 'cake.ant)

(defn cp [from to & opts]
  (ant Copy (into-map opts :file from :tofile to)))

(defn mv [from to & opts]
  (ant Move (into-map opts :file from :tofile to)))

(defn touch [file & opts]
  (ant Touch (into-map opts :file file)))

(defn rm [file & opts]
  (ant Delete (into-map opts :file file)))

(defn rmdir [file & opts]
  (ant Delete (into-map opts :dir file)))

(defn mkdir [file & opts]
  (ant Mkdir (into-map opts :dir file)))

(defn newer? [& args]
  (apply > (for [arg args]
             (if (number? arg)
               arg
               (.lastModified (if (string? arg) (file arg) arg))))))