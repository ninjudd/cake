(ns cake.tasks.jar-test
  (:use clojure.test helpers
        [cake.ant :only [fileset-seq]]
        [cake.file :only [file rmdir]]
	[clojure.java.shell :only [sh]]))

(use-fixtures :once in-test-project)

(deftest jar
  (let [results (cake "jar")
	jarfile (file "test-example-0.1.0-SNAPSHOT.jar")
	jarcontents (into #{}
		       (.split (:out (sh "jar" "tf" (.toString jarfile)))
			       "\n"))
	expected ["META-INF/cake/test-example/test-example/"
		  "META-INF/cake/" "META-INF/MANIFEST.MF"
		  "bar.clj"
		  "META-INF/maven/test-example/"
		  "cake.clj"
		  "META-INF/cake/test-example/test-example/project.clj"
		  "bar__init.class"
		  "baz.clj"
		  "META-INF/"
		  "bar$inc.class"
		  "foo.clj"
		  "META-INF/maven/test-example/test-example/"
		  "META-INF/maven/"
		  "META-INF/maven/test-example/test-example/pom.xml"
		  "META-INF/cake/test-example/"
		  "bar$foo.class"]]
    (is (.exists jarfile))
    (doseq [f expected]
      (is (contains? jarcontents f)))))

(deftest uberjar
  (let [results         (cake "uberjar")
	uberjarfile     (file "test-example-0.1.0-SNAPSHOT-standalone.jar")
	uberjarcontents (into #{}
			   (.split (:out (sh "jar" "tf"
					     (.toString uberjarfile)))
				   "\n"))]
    ;; this is a bit brittle, but that's the point, the test project should
    ;; be locked down to specific versions so nothing changes, ever.
    ;; forever ever. -l
    (is (= 2699 (count uberjarcontents)))))