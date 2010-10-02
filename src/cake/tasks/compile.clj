(ns cake.tasks.compile
  (:use cake cake.core cake.ant
        [cake.tasks.dependencies :only [os-name os-arch]]
        [cake.utils.find-namespaces :only [find-clojure-sources-in-dir read-file-ns-decl]])
  (:import [org.apache.tools.ant.taskdefs Copy Javac Java]))

(defn compile-java [src]
  (let [start (System/currentTimeMillis)]
    (when (.exists src)
      (ant Javac (assoc (:java-compile *project*)
                   :destdir     (file "classes")
                   :classpath   (classpath)
                   :srcdir      (path src)
                   :fork        true
                   :verbose     (verbose?)
                   :debug       true
                   :debug-level "source,lines"
                   :failonerror true)))
    (when (some #(newer? % start) (file-seq (file "classes")))
      (bake-restart))))

(defn classfile [ns]
  (file "classes"
    (.. (str ns)
        (replace "-" "_")
        (replace "." "/")
        (concat "__init.class"))))

(defn source-dir []
  (let [src (file "src" "clj")]
    (if (.exists src) src (file "src"))))

(defn stale-namespaces []
  (let [compile?
        (let [aot (:aot *project*)]
          (if (= :all aot)
            (constantly true)
            (if (= :exclude (first aot))
              (complement (partial contains? (set (rest aot))))
              (let [aot (set aot)]
                (fn [namespace]
                  (or (= namespace (:main *project*))
                      (contains? aot namespace)))))))]
    (remove nil?
      (for [sourcefile (find-clojure-sources-in-dir (source-dir))]
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
  (compile-java (file "src" "jvm"))
  (compile-clojure))

;; add actions to compile-native if you need to compile native libraries
;; see http://github.com/lancepantz/tokyocabinet for an example
(deftask compile-native)
