(ns cake.tasks.version-test
  (:use clojure.test helpers
	[cake.file :only [file]])
  (:require [clojure.java.io :as io]))

(comment
 (defn version-wrap [f]
   (cake "version" "0.1.0-SNAPSHOT")
   (f)
   (cake "version" "0.1.0-SNAPSHOT"))

 (use-fixtures :once in-test-project)
 (use-fixtures :each version-wrap)

 (defn assert-version [expected & [task-output]]
   (let [results (cake "version")]
     (testing "in current results from 'cake version'"
       (is (= (str "test-example " expected "\n")
	      (:out results))))
     (testing "in defproject contents"
       (is (= (str "(defproject test-example \"" expected "\"")
	      (first (line-seq (io/reader (file "project.clj")))))))
     (when task-output
       (testing "in output of called task"
	 (is (re-matches (re-pattern (str ".*-> test-example " expected "\n"))
			 (:out task-output)))))))

 (deftest initial-version
   (testing "initial version is set to 0.1.0-SNAPSHOT by fixture"
     (assert-version "0.1.0-SNAPSHOT")))

 (deftest explicit-version
   (testing "version set to 2.0.0"
     (let [results (cake "version" "2.0.0")]
       (assert-version "2.0.0"))))

 (deftest version-bumping-test
   (testing "version set with cake version bump"
     (let [results (cake "version" "bump")]
       (assert-version "0.1.0" results)))
   (testing "version set with cake version bump --major"
     (let [results (cake "version" "bump" "--major")]
       (assert-version "1.1.0" results)))
   (testing "version set with cake version bump --minor"
     (let [results (cake "version" "bump" "--minor")]
       (assert-version "1.2.0" results)))
   (testing "version set with cake version bump --patch"
     (let [results (cake "version" "bump" "--patch")]
       (assert-version "1.2.1" results)))
   (testing "version set with cake version bump --major --patch"
     (let [results (cake "version" "bump" "--major" "--patch")]
       (assert-version "2.2.2" results)))
   (testing "version set with cake version bump --snapshot"
     (let [results (cake "version" "bump" "--snapshot")]
       (assert-version "2.2.2-SNAPSHOT" results)))
   (testing "version set with cake version bump --major --snapshot"
     (let [results (cake "version" "bump" "--major" "--snapshot")]
       (assert-version "3.2.2-SNAPSHOT" results)))))