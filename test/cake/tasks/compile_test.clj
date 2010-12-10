(ns cake.tasks.compile-test
  (:use clojure.test helpers
        [uncle.core :only [fileset-seq]]
        [cake.file :only [file rmdir]]))

(use-fixtures :once in-test-project)

(deftest compile-clojure
  (let [classes (file "classes")]
    (rmdir classes)
    (let [results (cake "compile")
	  classes (fileset-seq {:dir classes :includes "*.class"})]
      (is (< 1 (count classes)))
      (is (some #(.contains (.getPath %) "speak") classes))
      (is (some #(.contains (.getPath %) "speak$_main") classes))
      (is (some #(.contains (.getPath %) "speak$sayhi") classes))
      (is (some #(.contains (.getPath %) "speak__init") classes)))))
