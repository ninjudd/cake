(ns cake.tasks.compile
  (:use cake cake.ant)
  (:import [org.apache.tools.ant.taskdefs Javac Java]
           [org.apache.tools.ant.types Path Environment$Variable]
           [java.io File]))

(defn compile-java [project]
  (ant Javac {:destdir     (File. (:root project) "classes")
              :classpath   (ant-path "src" "lib")
              :srcdir      (ant-path "src")
              :fork        true
              :debug       true
              :verbose     true
              :failonerror true})
  (println "compile-java complete."))

(defn compile-clj [project]
  (println (ant-path "src" "lib/*"))
  (ant Java {:classname   "clojure.lang.Compile"
             :classpath   (ant-path "src" "lib/*")
             :fork        true
             :failonerror true}
       ;; (doto Environment$Variable.
       ;; (.setKey "clojure.compile.path")
       ;; (.setValue "classes")))
       (.addSysproperty (make Environment$Variable {:key "clojure.compile.path" :value "classes"}))
  
       ;; (.addSysproperty (ant-var Environment$Variable {:key "clojure.compile.path" :value "classes"}))
       (println "compile-clj complete.")))
  
(deftask compile
  (println "compiling...")
  ;; (compile-java project)
  (compile-clj project))

