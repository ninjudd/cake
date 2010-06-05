(ns cake.tasks.compile
  (:use cake cake.ant
        [clojure.useful :only [include?]]
        [clojure.contrib.find-namespaces :only [find-clojure-sources-in-dir read-file-ns-decl]])
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

(defn stale? [sourcefile classfile]
  (> (.lastModified sourcefile) (.lastModified classfile)))

(defn aot
  "Return a function that takes a namespace and returns whether it should be aot compiled."
  [project]
  (let [aot (or (:aot project) (:namespaces project))]
    (if (= :all aot)
      (constantly true)
      (fn [namespace]
        (or (= namespace (:main project))
            (include? namespace aot))))))

(defn classfile [namespace]
  (str (.. (str namespace) (replace "-" "_") (replace "." "/"))
       "__init.class"))

(defn stale-namespaces [project]
  (let [compile? (aot project)]
    (remove nil?
      (for [sourcefile (find-clojure-sources-in-dir (File. (:source-path project)))]
        (let [namespace  (second (read-file-ns-decl sourcefile))
              classfile (File. (:compile-path project) (classfile namespace))]
          (when (and (compile? namespace) (stale? sourcefile classfile))
            namespace))))))

(defn compile-clojure [project]
  (ant Java {:classname   "clojure.lang.Compile"
             :classpath   (classpath project)
             :fork        true
             :failonerror true}
       (sys {:clojure.compile.path (:compile-path project)})
       (args (map name (stale-namespaces project)))))

(deftask compile
  (compile-java project)
  (compile-clojure project))
