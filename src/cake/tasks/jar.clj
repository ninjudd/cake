(ns cake.tasks.jar
  (:use cake cake.ant)
  (:import [org.apache.tools.ant.taskdefs Jar]
           [org.apache.tools.ant.taskdefs.optional.ssh Scp]
           [org.apache.tools.ant.types FileSet ZipFileSet]
           [java.io File]))

(defn jarfile [project]
  (format "%s-%s.jar" (:artifact-id project) (:version project)))

(defn jar [project]
  (let [jarfile (jarfile project)
        maven   (format "META-INF/maven/%s/%s" (:group-id project) (:artifact-id project))
        cake    (format "META-INF/cake/%s/%s"  (:group-id project) (:artifact-id project))]
    (println "building" jarfile)
    (ant Jar {:dest-file jarfile}
      (add-fileset ZipFileSet {:dir (:root project) :prefix maven :includes "pom.xml"})
      (add-fileset ZipFileSet {:dir (:root project) :prefix cake  :includes "*.clj"})
      (add-fileset FileSet {:dir (:compile-path project)})
      (add-fileset FileSet {:dir (:source-path project)}))))

(deftask jar => compile
  (jar project))

(defn keyfile [files]
  (let [sshdir (File. (System/getProperty "user.home") ".ssh")]
    (first
      (filter #(.exists %)
        (map #(File. sshdir %) files)))))

(deftask release => jar
  (ant Scp {:todir "clojars@clojars.org:" :trust true :keyfile (keyfile ["id_rsa" "id_dsa" "identity"])}
    (add-fileset FileSet {:dir (:root project) :includes "pom.xml"})
    (add-fileset FileSet {:dir (:root project) :includes (jarfile project)})))
