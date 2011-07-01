(ns cake.tasks.compile
  (:use cake
        [cake.core :only [deftask bake invoke]]
        [uncle.core :only [ant add-fileset fileset-seq]]
        [cake.file :only [file newer? mkdir]]
        [cake.project :only [reset-classloaders! with-classloader classpath]]
        [bake.core :only [verbose? debug? log os-name os-arch]]
        [cake.utils :only [sudo prompt-read]]
        [useful.string :only [pluralize]])
  (:import [org.apache.tools.ant.taskdefs Copy Javac Java]))

(declare copy-native)

(defn compile-java [src & [dest]]
  (let [start (System/currentTimeMillis)
        dest  (file (or dest (first (:compile-path *project*))))]
    (ant Javac (merge {:destdir     dest
                       :classpath   (classpath)
                       :srcdir      src
                       :fork        true
                       :verbose     (verbose?)
                       :debug       true
                       :debug-level "source,lines"
                       :target      "1.5"
                       :failonerror true}
                      (:java-compile *project*)))
    (when (some #(newer? % start) (file-seq dest))
      (reset-classloaders!))))

(deftask compile-java #{compile-native}
  (copy-native)
  (compile-java (:source-path *project*)))

(defn compile-clojure [source-path compile-path namespaces]
  (mkdir compile-path)
  (bake (:use [bake.compile :only [compile-stale]])
    [source-path  source-path
     compile-path compile-path
     namespaces   namespaces]
    (compile-stale source-path compile-path namespaces)))

(defn copy-native []
  (let [os-name (os-name)
        os-arch (os-arch)]
    (ant Copy {:todir (format "native/%s/%s" os-name os-arch)}
         (add-fileset {:dir (format "build/native/%s/%s/lib" os-name os-arch)}))))

(deftask compile #{compile-native compile-java}
  "Compile all clojure and java source files. Use 'cake compile force' to recompile."
  (when (= "force" (first (:compile *opts*)))
    (invoke clean {}))
  (let [jar-classes (file "build" "jar")]
    (mkdir jar-classes)
    (with-classloader [jar-classes]
      (compile-clojure (:source-path *project*) (first (:compile-path      *project*)) (:aot      *project*))
      (compile-clojure (:test-path   *project*) (first (:test-compile-path *project*)) (:aot-test *project*))
      (compile-clojure (:source-path *project*) (.getPath jar-classes)                 [(:main *project*)]))))

;; add actions to compile-native if you need to compile native libraries
;; see http://github.com/flatland/tokyocabinet for an example
(deftask compile-native)

(deftask install-native #{compile-native}
  (copy-native)
  (let [files (vec (map str (fileset-seq {:dir (file (:library-path *project*)
                                                     "native")
                                          :includes "*"})))
        default "/usr/lib/java/"
        dest (prompt-read (format "java.library.path [%s]:" default))
        dest (if (= "" dest) default dest)]
    (apply sudo "cp" (conj files dest))))
