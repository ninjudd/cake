(ns cake.tasks.compile
  (:use cake cake.ant
        [clojure.contrib.find-namespaces :only [find-namespaces-in-dir]])
  (:import [org.apache.tools.ant.taskdefs Javac Java]
           [java.io File]))

(defn compile-java [project]
  (let [dest (File. (:compile-path project))]
    (.mkdirs dest)
    (ant Javac {:destdir           dest
                :classpath         (classpath project)
                :srcdir            (path (:source-path project))
                :fork              true
                :failonerror       true
                :includeantruntime false})))

(defn to-compile [project]
  (let [aot (or (:aot project) (:namespaces project))
        aot (cond (string? aot) (vector aot)
                  (= :all aot)  (find-namespaces-in-dir (:source-path project))
                  :else         aot)]
    (if-let [main (:main project)]
      (conj aot main)
      aot)))

(defn compile-clj [project]
  (ant Java {:classname   "clojure.lang.Compile"
             :classpath   (classpath project)
             :fork        true
             :failonerror true}
       (env {:clojure.compile.path "classes"})
       (args (to-compile project))))

(deftask compile
  (compile-java project)
  (compile-clj project))
