(ns cake.tasks.jar-test
  (:use clojure.test helpers
        [uncle.core :only [fileset-seq]]
        [cake.file :only [file rm rmdir]]
	[clojure.java.shell :only [sh]]))

(use-fixtures :once in-test-project)

(defn get-jar-contents [jarfile]
  (into #{} (.split (:out (sh "jar" "tf" (.toString jarfile))) "\n")))

(defn assert-jar
  "passed a file object for a jar and optionally a list of files expected to be contained within. Asserts that the jar exists and contains every file expected."
  [jar & [expected-files]]
  (let [jarfile (file jar)] 
    (is (.exists jarfile))
    (when expected-files
      (let [jarcontents (get-jar-contents jarfile)]
	(doseq [f expected-files]
	  (is (contains? jarcontents f)))))))

(def project-name "test-example")
(def version "0.1.0-SNAPSHOT")
(def base (str project-name "-" version))
(def uberjar (str base "-standalone.jar"))
(def jar (str base ".jar"))
(def war (str base ".war"))
(def bin project-name)

(deftest test-jar
  (rm jar)
  (cake "jar")
  (assert-jar jar
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
	       "META-INF/cake/test-example/"]))

(deftest test-uberjar
  (rm jar)
  (rm uberjar)
  (cake "uberjar")
  (test-jar)
  (assert-jar uberjar)
  (is (> (count (get-jar-contents (file uberjar)))
	 (count (get-jar-contents (file jar))))))

(deftest test-bin
  (rm bin)
  (cake "bin")
  (assert-jar jar)
  (assert-jar uberjar)
  (assert-jar bin)
  (is (= "hi!\n" (:out (sh (.toString (file bin)))))))

(deftest test-war
  (rm war)
  (cake "war")
  (assert-jar war ["META-INF/"
		   "META-INF/MANIFEST.MF"
		   "WEB-INF/"
		   "WEB-INF/classes/"
		   "WEB-INF/classes/bar.clj"
		   "WEB-INF/classes/baz.clj"
		   "WEB-INF/classes/foo.clj"
		   "WEB-INF/classes/servlet.clj"
		   "WEB-INF/classes/speak.clj"
		   "WEB-INF/web.xml"
		   "WEB-INF/classes/servlet$route.class"
		   "WEB-INF/classes/servlet.class"
		   "WEB-INF/classes/servlet__init.class"
		   "WEB-INF/classes/speak$_main.class"
		   "WEB-INF/classes/speak$sayhi.class"
		   "WEB-INF/classes/speak.class"
		   "WEB-INF/classes/speak__init.class"
		   "WEB-INF/classes/cake.clj"]))

(deftest test-uberwar
  (rm war)
  (cake "uberwar")
  (assert-jar war ["WEB-INF/lib/"
		   "WEB-INF/lib/clojure-1.2.0.jar"
		   "WEB-INF/lib/clojure-contrib-1.2.0.jar"]))

(deftest test-install
  (let [local-repo "~/.m2/repository/"
	repo-dir (file local-repo project-name project-name version)]
    (rmdir (file local-repo project-name))
    (rm jar)
    (cake "install")
    (assert-jar (file repo-dir jar))
    (is (.exists (file repo-dir (str base ".pom"))))
    (is (.exists (file repo-dir "maven-metadata-local.xml")))))