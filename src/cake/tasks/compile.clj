(ns cake.tasks.compile
  (:use cake cake.core cake.ant
        [useful :only [include?]]
        [cake.tasks.dependencies :only [os-name os-arch]]
        [cake.contrib.find-namespaces :only [find-clojure-sources-in-dir read-file-ns-decl]])
  (:import [org.apache.tools.ant.taskdefs Copy Javac Java]))

(defn compile-java []
  (let [start (System/currentTimeMillis)
        src   (file "src" "jvm")]
    (when (.exists src)
      (ant Javac {:destdir     (file "classes")
                  :classpath   (classpath)
                  :srcdir      (path src)
                  :fork        true
                  :debug       true
                  :debug-level "source,lines"
                  :failonerror true}))
    (when (some #(newer? % start) (file-seq (file "classes")))
      (bake-restart))))

(defn classfile [ns]
  (file "classes"
    (.. (str ns)
        (replace "-" "_")
        (replace "." "/")
        (concat "__init.class"))))

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
        (let [namespace (second (read-file-ns-decl sourcefile))
              classfile (classfile namespace)]
          (when (and (compile? namespace) (newer? sourcefile classfile))
            namespace))))))

(defn compile-clojure []
  (when-let [stale (seq (stale-namespaces))]
    (bake [libs stale]
      (doseq [lib libs]
        (compile lib)))
    (bake-restart)))

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

(deftask compile #{deps compile-native}
  "Compile all clojure and java source files. Use 'cake compile force' to recompile."
  (copy-native)
  (compile-java)
  (compile-clojure))

;; add actions to compile-native if you need to compile native libraries
;; see http://github.com/lancepantz/tokyocabinet for an example
(deftask compile-native)
