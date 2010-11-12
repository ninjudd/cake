(ns cake.tasks.jar-test
  (:use clojure.test helpers
        [cake.ant :only [fileset-seq]]
        [cake.file :only [file rmdir]]
	[clojure.java.shell :only [sh]]))

(use-fixtures :once in-test-project)

(deftest jar
  (let [results  (cake "jar")
	jarfile  (file "test-example-0.1.0-SNAPSHOT.jar")
	jarfiles (into #{}
		       (.split (:out (sh "jar" "tf" (.toString jarfile)))
			       "\n"))]
    (is (.exists jarfile))
    (is (contains? jarfiles "META-INF/"))
    (is (contains? jarfiles "META-INF/cake/test-example/test-example/"))
    (is (contains? jarfiles "META-INF/cake/"))
    (is (contains? jarfiles "META-INF/MANIFEST.MF"))
    (is (contains? jarfiles "bar.clj"))
    (is (contains? jarfiles "META-INF/maven/test-example/"))
    (is (contains? jarfiles "cake.clj"))
    (is (contains? jarfiles "META-INF/cake/test-example/test-example/project.clj"))
    (is (contains? jarfiles "bar__init.class"))
    (is (contains? jarfiles "baz.clj"))
    (is (contains? jarfiles "META-INF/"))
    (is (contains? jarfiles "bar$inc.class"))
    (is (contains? jarfiles "foo.clj"))
    (is (contains? jarfiles "META-INF/maven/test-example/test-example/"))
    (is (contains? jarfiles "META-INF/maven/"))
    (is (contains? jarfiles "META-INF/maven/test-example/test-example/pom.xml"))
    (is (contains? jarfiles "META-INF/cake/test-example/"))
    (is (contains? jarfiles "bar$foo.class"))))

(deftest uberjar
  (let [results      (cake "uberjar")
	uberjarfile  (file "test-example-0.1.0-SNAPSHOT-standalone.jar")
	uberjarfiles (into #{}
			   (.split (:out (sh "jar" "tf"
					     (.toString uberjarfile)))
				   "\n"))]
    ;; this is a bit brittle, but that's the point, the test project should
    ;; be locked down to specific versions so nothing changes, ever.
    ;; forever ever. -l
    (is (= 2699 (count uberjarfiles)))))
