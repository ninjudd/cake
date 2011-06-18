(ns cake.tasks.compile
  (:use cake
        [cake.core :only [deftask bake]]
        [uncle.core :only [ant add-fileset fileset-seq path classpath]]
        [cake.file :only [file newer?]]
        [cake.project :only [reset-classloaders! with-classloader]]
        [bake.core :only [verbose? debug? log os-name os-arch]]
        [cake.utils :only [sudo prompt-read]]
        [useful :only [pluralize]])
  (:import [org.apache.tools.ant.taskdefs Copy Javac Java]))

(declare copy-native)

(defn compile-java [src]
  (let [start (System/currentTimeMillis)]
    (when (.exists src)
      (ant Javac (merge {:destdir     (file "classes")
                         :classpath   (classpath)
                         :srcdir      (path src)
                         :fork        true
                         :verbose     (verbose?)
                         :debug       true
                         :debug-level "source,lines"
                         :target      "1.5"
                         :failonerror true}
                        (:java-compile *project*))))
    (when (some #(newer? % start) (file-seq (file "classes")))
      (reset-classloaders!))))

(deftask compile-java #{deps compile-native}
  (copy-native)
  (compile-java (file "src" "jvm")))

(defn source-dir []
  (let [src (file "src" "clj")]
    (if (.exists src) src (file "src"))))

(defn compile-clojure [source-path compile-path namespaces]
  (.mkdirs compile-path)
  (bake (:use [bake.compile :only [compile-stale]])
        [source-path  (.getPath source-path)
         compile-path (.getPath compile-path)
         namespaces   namespaces]
        (compile-stale source-path compile-path namespaces)))

(defn copy-native []
  (let [os-name (os-name)
        os-arch (os-arch)]
    (ant Copy {:todir (format "native/%s/%s" os-name os-arch)}
         (add-fileset {:dir (format "build/native/%s/%s/lib" os-name os-arch)}))))

(deftask compile #{deps compile-native compile-java}
  "Compile all clojure and java source files. Use 'cake compile force' to recompile."
  (let [jar-classes (file "build" "jar")]
    (.mkdirs jar-classes)
    (with-classloader [jar-classes]
      (compile-clojure (source-dir)  (file "classes")        (:aot *project*))
      (compile-clojure (file "test") (file "test" "classes") (:aot-test *project*))
      (compile-clojure (source-dir)  jar-classes             [(:main *project*)]))))

;; add actions to compile-native if you need to compile native libraries
;; see http://github.com/lancepantz/tokyocabinet for an example
(deftask compile-native)

(deftask install-native #{compile-native}
  (copy-native)
  (let [files (vec (map str (fileset-seq {:dir (file "lib" "native")
                                          :includes "*"})))
        default "/usr/lib/java/"
        dest (prompt-read (format "java.library.path [%s]:" default))
        dest (if (= "" dest) default dest)]
    (apply sudo "cp" (conj files dest))))
