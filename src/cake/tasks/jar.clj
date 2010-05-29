(ns cake.tasks.jar
  (:use cake cake.ant)
  (:import [org.apache.tools.ant.taskdefs Jar]
           [org.apache.tools.ant.types FileSet ZipFileSet]))

(defn jar [project]
  (let [jarfile (format "%s-%s.jar" (:artifact-id project) (:version project))
        maven   (format "META-INF/maven/%s/%s" (:group-id project) (:artifact-id project))
        cake    (format "META-INF/cake/%s/%s"  (:group-id project) (:artifact-id project))]
    (println "building" jarfile)
    (ant Jar {:dest-file jarfile}
      (add-fileset ZipFileSet {:dir (:root project) :prefix maven :includes "pom.xml"})
      (add-fileset ZipFileSet {:dir (:root project) :prefix cake  :includes "*.clj"})
      (add-fileset FileSet {:dir (:compile-path project)})
      (add-fileset FileSet {:dir (:source-path project)}))))

(deftask jar
  (jar project))