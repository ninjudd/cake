(ns cake.tasks.compile
  (:use cake
        cake.ant
        [clojure.contrib.find-namespaces :only [find-namespaces-in-dir]])
  (:import [org.apache.tools.ant.taskdefs Javac Java]
           [org.apache.tools.ant.types Path Environment$Variable]
           [java.io File]))

(defn compile-java [project]
  (ant Javac {:destdir      (File. (:root project) "classes")
              :classpath    (ant-path "src" "lib/*")
              :srcdir       (ant-path "src")
              :fork         true
              :failonerror  true
              :includeantruntime true}))

(defn to-compile [project]
  (let [aot  (or (:aot project) (:namespaces project))
        aot  (cond (string? aot) (vector aot)
                   (= :all aot) (find-namespaces-in-dir (File. (:root project) "src"))
                   :else aot)]
    (if (:main project)
      (into [] (concat aot [(:main project)]))
      aot)))

(defn compile-clj [project]
  (ant Java {:classname   "clojure.lang.Compile"
             :classpath   (ant-path "src" "lib/*")
             :fork        true
             :failonerror true}
       (.addSysproperty (make Environment$Variable {:key "clojure.compile.path" :value "classes"}))
       (args (to-compile project))))

(deftask compile
  (compile-java project)
  (compile-clj project)
  (println "compile complete."))

