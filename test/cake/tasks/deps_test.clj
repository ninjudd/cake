(ns cake.tasks.deps-test
  (:use clojure.test helpers
        [cake.ant :only [fileset-seq]]
        [cake.file :only [file rmdir]]))

(use-fixtures :once in-test-project)

(deftest test-deps
  (let [lib (file "lib")]
    (rmdir lib)
    (let [results  (cake "deps")
          jars     (fileset-seq {:dir lib                 :includes "*.jar"})
          dev-jars (fileset-seq {:dir (file lib "dev")    :includes "*.jar"})
          native   (fileset-seq {:dir (file lib "native") :includes "*"})]
      (is (< 1 (count jars)))
      (is (some #(.contains (.getPath %) "clojure-1.2.0")         jars))
      (is (some #(.contains (.getPath %) "clojure-contrib-1.2.0") jars))
      (is (some #(.contains (.getPath %) "tokyocabinet-1.23")     jars))
      (is (< 1 (count dev-jars)))
      (is (some #(.contains (.getPath %) "autodoc-0.7.1") dev-jars))
      (is (some #(.contains (.getPath %) "enlive-1.0.0") dev-jars)) ;; autodoc dependency
      (is (not-any? #(.contains (.getPath %) "clojure-1.2.0") dev-jars))
      (is (< 1 (count native)))
      (is (some #(.contains (.getPath %) "libjtokyocabinet") native))
      (is (some #(.contains (.getPath %) "libtokyocabinet") native)))))
