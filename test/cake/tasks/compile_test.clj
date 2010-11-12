(ns cake.tasks.compile-test
  (:use clojure.test helpers
        [cake.ant :only [fileset-seq]]
        [cake.file :only [file rmdir]]))

(use-fixtures :once in-test-project)

(deftest compile-clojure
  (let [classes (file "classes")]
    (rmdir classes)
    (let [results (cake "compile")
	  classes (fileset-seq {:dir classes :includes "*.class"})]
      (is (< 1 (count classes)))
      (is (some #(.contains (.getPath %) "bar$foo")   classes))
      (is (some #(.contains (.getPath %) "bar$inc")   classes))
      (is (some #(.contains (.getPath %) "bar__init") classes)))))
