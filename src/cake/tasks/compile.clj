(ns cake.tasks.compile
  (:use cake cake.ant)
  (:import [org.apache.tools.ant.taskdefs Javac]
           [org.apache.tools.ant.types Path]
           [java.io File]))

(defn compile-java [project]
  (let [root       (:root project)
        classpath  (for [dir ["/src" "/lib*"]] (str root dir))]
    (ant Javac {:destdir (File. root "classes")
                :classpath (ant-path "src" "lib")
                :srcdir (ant-path "src")
                :fork true
                :debug true
                :verbose true
                :failonerror true})
    (println "javac complete.")))

(deftask compile
  ;; (println (seq (.getURLs (java.lang.ClassLoader/getSystemClassLoader))))
  (println "compiling...")
  (compile-java project)
  ;; (println (ns-name *ns*))
  )
