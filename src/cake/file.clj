(ns cake.file
  (:use cake
        cake.core
        cake.ant
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

(defn cp [from to]
  (ant Copy {:file (file from)
             :tofile (file to)}))

(defn mv [from to]
  (ant Move {:file (file from)
             :tofile (file to)}))

(defn touch [& args]
  (ant Touch {:file (apply file args)}))

(defn rm [f]
  (ant Delete {:file (file f)}))

(defn rmdir [f]
  (ant Delete {:dir (file f)}))

(defn mkdir [f]
  (ant Mkdir {:dir (file f)}))

(defn mtime< [a b]
  (< (.lastModified (file a))
     (.lastModified (file b))))
