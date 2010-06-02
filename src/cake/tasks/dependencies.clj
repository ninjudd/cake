(ns cake.tasks.dependencies
  (:use cake cake.ant)
  (:import [org.apache.maven.artifact.ant DependenciesTask RemoteRepository WritePomTask Pom]
           [org.apache.maven.model Dependency License]
           [org.apache.tools.ant.taskdefs Copy Delete]))

(def repositories
  {"central"           "http://repo1.maven.org/maven2"
   "clojure"           "http://build.clojure.org/releases"
   "clojure-snapshots" "http://build.clojure.org/snapshots"
   "clojars"           "http://clojars.org/repo/"})

(defn add-license [task [license url]]
  (when license
    (.addConfiguredLicense task
      (make License {:name license :url url}))))

(defn add-repositories [task repositories]
  (doseq [[id url] repositories]
    (.addConfiguredRemoteRepository task
      (make RemoteRepository {:id id :url url}))))

(defn add-dependencies [task dependencies]
  (doseq [[dep version] dependencies]
    (let [dep (make Dependency {:group-id (group dep) :artifact-id (name dep) :version version})]
      (if (instance? Pom task)
        (.addConfiguredDependency task dep)
        (.addDependency task dep)))))

(defn deps [project]
  (ant DependenciesTask {:fileset-id "cake.dep.fileset" :path-id (:name project)}
    (add-repositories repositories)
    (add-dependencies (project :dependencies)))
  (let [dest (java.io.File. (:library-path project))]
    (.mkdirs dest)
    (ant Delete {} (add-fileset {:dir dest :includes "*.jar"}))
    (ant Copy {:todir dest :flatten true}
      (.addFileset (get-reference "cake.dep.fileset")))))

(defn pom [project]
  (let [refid "cake.pom"
        file  (java.io.File. (project :root) "pom.xml")
        attrs (select-keys project [:artifact-id :group-id :version :name :description])]
    (ant Pom (assoc attrs :id refid)
      (add-license (project :license))
      (add-dependencies (project :dependencies)))
    (ant WritePomTask {:pom-ref-id refid :file file})))

(deftask deps "Fetch dependencies and create pom.xml"
  (println "Fetching dependencies...")
  (deps project)
  (pom project))
