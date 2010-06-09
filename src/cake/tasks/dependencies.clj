(ns cake.tasks.dependencies
  (:use cake cake.ant)
  (:import [org.apache.maven.artifact.ant DependenciesTask RemoteRepository WritePomTask Pom]
           [org.apache.maven.model Dependency Exclusion License]
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

(defn exclusion [dep]
  (make Exclusion {:group-id (group dep) :artifact-id (name dep)}))

(defn- add-dep [task dep]
  (if (instance? Pom task)
    (.addConfiguredDependency task dep)
    (.addDependency task dep)))

(defn add-dependencies [task deps]
  (doseq [[dep version & opts] deps]
    (let [opts (apply array-map opts)]
      (add-dep task
       (make Dependency
         {:group-id    (group dep)
          :artifact-id (name dep)
          :version     version
          :exclusions  (map exclusion (:exclusions opts))})))))

(defn fetch-deps [dependencies dest]
  (ant DependenciesTask {:fileset-id "cake.dep.fileset" :path-id (:name project)}
       (add-repositories repositories)
       (add-dependencies dependencies))
  (.mkdirs dest)
  (ant Delete {} (add-fileset {:dir dest :includes "*.jar"}))
  (ant Copy {:todir dest :flatten true}
       (.addFileset (get-reference "cake.dep.fileset"))))

(defn pom [project]
  (let [refid "cake.pom"
        file  (file "pom.xml")
        attrs (select-keys project [:artifact-id :group-id :version :name :description])]
    (ant Pom (assoc attrs :id refid)
      (add-license (project :license))
      (add-dependencies (project :dependencies)))
    (ant WritePomTask {:pom-ref-id refid :file file})))

(deftask deps "Fetch dependencies and create pom.xml"
  (println "Fetching dependencies...")
  (fetch-deps (:dependencies project)     (file "lib"))
  (fetch-deps (:dev-dependencies project) (file "lib/dev"))
  (pom project))
