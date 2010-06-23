(ns cake.tasks.compile
  (:use cake cake.ant
        [useful :only [include?]]
        [cake.contrib.find-namespaces :only [find-clojure-sources-in-dir read-file-ns-decl]])
  (:import [org.apache.tools.ant.taskdefs Javac Java]))

(defn compile-java [project]
  (let [src (file "src" "jvm")]
    (when (.exists src)
      (ant Javac {:destdir     (file "classes")
                  :classpath   (classpath project)
                  :srcdir      (path src)
                  :fork        true
                  :failonerror true}))))

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
  (str (.. (str namespace) (replace "-" "_") (replace "." "/")) "__init.class"))

(defn stale-namespaces [project]
  (let [compile? (aot project)]
    (remove nil?
      (for [sourcefile (find-clojure-sources-in-dir (file "src"))]
        (let [namespace  (second (read-file-ns-decl sourcefile))
              classfile (file "classes" (classfile namespace))]
          (when (and (compile? namespace) (stale? sourcefile classfile))
            namespace))))))

(defn compile-clojure [project]
  (bake [namespaces (stale-namespaces project)]
    (doseq [lib namespaces]
      (compile lib))))

(deftask compile
  "Compile all clojure and java source files."
  (compile-java project)
  (compile-clojure project))
