(ns cake.tasks.compile
  (:use cake cake.ant
        [useful :only [include?]]
        [cake.tasks.dependencies :only [os-name os-arch]]
        [cake.contrib.find-namespaces :only [find-clojure-sources-in-dir read-file-ns-decl]])
  (:import [org.apache.tools.ant.taskdefs Copy Javac Java]))

(defn compile-java []
  (let [src (file "src" "jvm")]
    (when (.exists src)
      (ant Javac {:destdir     (file "classes")
                  :classpath   (classpath)
                  :srcdir      (path src)
                  :fork        true
                  :failonerror true}))))

(defn stale? [sourcefile classfile]
  (> (.lastModified sourcefile) (.lastModified classfile)))

(defn classfile [namespace]
  (str (.. (str namespace) (replace "-" "_") (replace "." "/")) "__init.class"))

(defn stale-namespaces []
  (let [compile?
        (let [aot (:aot *project*)]
          (if (= :all aot)
            (constantly true)
            (fn [namespace]
              (or (= namespace (:main *project*))
                  (include? namespace aot)))))]
    (remove nil?
      (for [sourcefile (find-clojure-sources-in-dir (file "src"))]
        (let [namespace  (second (read-file-ns-decl sourcefile))
              classfile (file "classes" (classfile namespace))]
          (when (and (compile? namespace) (stale? sourcefile classfile))
            namespace))))))

(defn compile-clojure []
  (when-let [stale (seq (stale-namespaces))]
    (bake [libs stale]
      (doseq [lib libs]
        (compile lib)))))

(defn add-native-libs [task dir libs]
  (doseq [lib libs]
    (let [libname (System/mapLibraryName (str lib))
          libfile (file dir libname)]
      (if (.exists libfile)
        (add-fileset task {:file libfile})
        (when (= "macosx" (os-name))
          (add-fileset task {:file (file dir (.replaceAll libname "\\.jnilib" ".dylib"))}))))))

(defn copy-native []
  (when-let [libs (:native-libs *project*)]
    (ant Copy {:todir (format "native/%s/%s" (os-name) (os-arch))}
         (add-native-libs "build/native/lib" libs))))

(deftask compile #{compile-native}
  "Compile all clojure and java source files."
  (copy-native)
  (compile-java)
  (compile-clojure))

;; add actions to compile-native if you need to compile native libraries
;; see http://github.com/lancepantz/tokyocabinet for an example
(deftask compile-native)
