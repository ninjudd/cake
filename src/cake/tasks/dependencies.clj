(ns cake.tasks.dependencies
  (:use cake cake.ant)
  (:import [org.apache.maven.artifact.ant DependenciesTask RemoteRepository]
           [org.apache.maven.model Dependency]
           [org.apache.tools.ant.taskdefs Copy]))

(def repositories
  {"central"           "http://repo1.maven.org/maven2"
   "clojure"           "http://build.clojure.org/releases"
   "clojure-snapshots" "http://build.clojure.org/snapshots"
   "clojars"           "http://clojars.org/repo/"})

(defn add-repositories [task repositories]
  (doseq [[id url] repositories]
    (.addConfiguredRemoteRepository task
      (make RemoteRepository {:id id :url url}))))

(defn add-dependencies [task dependencies]
  (doseq [[dep version] dependencies]
    (.addDependency task
       (make Dependency {:group-id (group dep) :artifact-id (name dep) :version version}))))

(defn copy-dependencies [task dest]
  (let [dest    (java.io.File. dest)
        fileset (.getReference (.getProject task) (.getFilesetId task))]
    (.mkdirs dest)
    (ant Copy {:todir dest :flatten true}
      (.addFileset fileset))))

(defn deps [project]
  (-> (ant DependenciesTask {:fileset-id "dependency.fileset" :path-id (:name project)}
        (add-repositories repositories)
        (add-dependencies (project :dependencies)))
      (copy-dependencies (:library-path project))))

(deftask deps "Fetch dependencies and create pom.xml"
  (deps project))
