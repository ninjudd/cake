(ns cake.tasks.jar-test
  (:use clojure.test helpers
        [cake.ant :only [fileset-seq]]
        [cake.file :only [file rm]]
	[clojure.java.shell :only [sh]]))

(use-fixtures :once in-test-project)

(defn get-jar-contents [jarfile]
  (into #{} (.split (:out (sh "jar" "tf" (.toString jarfile))) "\n")))

(defn assert-jar
  "passed a file object for a jar and optionally a list of files expected to be contained within. Asserts that the jar exists and contains every file expected."
  [jarfile & [expected-files]]
  (is (.exists jarfile))
  (when expected-files
    (let [jarcontents (get-jar-contents jarfile)]
      (doseq [f expected-files]
	(is (contains? jarcontents f))))))

(deftest jar
  (let [jarfile (file "test-example-0.1.0-SNAPSHOT.jar")]
    (rm jarfile)
    (cake "jar")
    (assert-jar jarfile
		["META-INF/cake/test-example/test-example/"
		 "META-INF/cake/"
		 "META-INF/MANIFEST.MF"
		 "speak$sayhi.class"
		 "speak.clj"
		 "bar.clj"
		 "META-INF/maven/test-example/"
		 "cake.clj"
		 "META-INF/cake/test-example/test-example/project.clj"
		 "speak__init.class"
		 "baz.clj"
		 "speak$_main.class"
		 "META-INF/"
		 "foo.clj"
		 "META-INF/maven/test-example/test-example/"
		 "META-INF/maven/"
		 "speak.class"
		 "META-INF/maven/test-example/test-example/pom.xml"
		 "META-INF/cake/test-example/"])))

(deftest uberjar
  (cake "uberjar")
  (let [uberjarfile (file "test-example-0.1.0-SNAPSHOT-standalone.jar")]
    (assert-jar uberjarfile)
    ;; this is a bit brittle, but that's the point, the test project should
    ;; be locked down to specific versions so nothing changes, ever.
    ;; forever ever. -lance
    (is (= 2701 (count (get-jar-contents uberjarfile))))))

(deftest bin
  (let [binfile (file "test-example")]
    (rm binfile)
    (cake "bin")
    (is (= "hi!\n" (:out (sh (.toString binfile)))))))