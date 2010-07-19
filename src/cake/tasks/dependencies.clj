(ns cake.tasks.dependencies
  (:use cake cake.ant
        [clojure.java.shell :only [sh]])
  (:import [org.apache.maven.artifact.ant DependenciesTask RemoteRepository WritePomTask Pom]
           [org.apache.tools.ant.taskdefs Copy Delete ExecTask Move]
           [org.apache.maven.model Dependency Exclusion License]
           [java.io File]))

(def repositories
  [["central"           "http://repo1.maven.org/maven2"]
   ["clojure"           "http://build.clojure.org/releases"]
   ["clojure-snapshots" "http://build.clojure.org/snapshots"]
   ["clojars"           "http://clojars.org/repo/"]])

(defn os-name []
  (let [name (System/getProperty "os.name")]
    (condp #(.startsWith %2 %1) name
      "Linux"    "linux"
      "Mac OS X" "macosx"
      "SunOS"    "solaris"
      "Windows"  "windows"
      "unknown")))

(defn os-arch []
  (let [arch (System/getProperty "os.arch")]
    (condp = arch
      "amd64" "x86_64"
      "i386"  "x86"
      arch)))

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
  (doseq [[dep version & opts] deps :let [opts (apply array-map opts)]]
    (add-dep task
      (make Dependency
        {:group-id    (group dep)
         :artifact-id (name dep)
         :version     version
         :exclusions  (map exclusion (:exclusions opts))}))))

(defn subproject-path [dep]
  (when config
    (config (str "subproject." (name dep)))))

(defn subproject? [[dep version & opts]]
  (not (nil? (subproject-path dep))))

(defn add-jarset [task path exclusions]
  (let [exclusions (map #(re-pattern (str % "-\\d.*")) exclusions)]
    (doseq [jar (fileset-seq {:dir path :includes "*.jar"}) :let [name (.getName jar)]]
      (when (not-any? #(re-matches % name) exclusions)
        (add-fileset task {:file jar})))))

(defn glob [dir pattern]
  (let [pattern (re-pattern pattern)
        match?  #(re-matches pattern (.getName %))]
    (filter match? (.listFiles (File. dir)))))

(defn fetch-subprojects [deps dest]
  (doseq [[dep _ & opts] deps :let [opts (apply array-map opts)]]
    (when-let [path (subproject-path dep)]
      (ant ExecTask {:executable "cake" :dir path :failonerror true} (args ["deps"]))
      (ant ExecTask {:executable "cake" :dir path :failonerror true} (args ["jar" "--clean"]))
      (let [jar (first (glob path (str (name dep) "-.*jar")))]
        (when-not (.exists jar)
          (throw (Exception. (str "unable to locate subproject jar: " (.getPath jar)))))
        (ant Copy {:todir dest}
             (add-fileset {:file jar})
             (add-jarset (File. path "lib") (:exclusions opts)))))))

(defn extract-native [jars dest]
  (doseq [jar jars]
    (ant Copy {:todir dest :flatten true}
         (add-zipfileset {:src jar :includes (format "native/%s/%s/*" (os-name) (os-arch))}))))

(defn fetch-deps [deps dest]
  (fetch-subprojects deps dest)  
  (when-let [deps (seq (remove subproject? deps))]
    (let [ref-id (str "cake.deps.fileset." (.getName dest))]
      (ant DependenciesTask {:fileset-id ref-id :path-id (:name project)}
           (add-repositories (into repositories (:repositories project)))
           (add-dependencies deps))
      (ant Copy {:todir dest :flatten true}
           (.addFileset (get-reference ref-id)))))
  (extract-native
   (fileset-seq {:dir dest :includes "*.jar"})
   (str dest "/native")))

(defn pom [project]
  (let [refid "cake.pom"
        file  (file "pom.xml")
        attrs (select-keys project [:artifact-id :group-id :version :name :description])]
    (ant Pom (assoc attrs :id refid)
      (add-license (project :license))
      (add-dependencies (project :dependencies)))
    (ant WritePomTask {:pom-ref-id refid :file file})))

(deftask deps "Fetch dependencies and create pom.xml."
  (println "Fetching dependencies...")
  (fetch-deps (:dependencies project) (file "build/lib"))
  (fetch-deps (:dev-dependencies project) (file "build/lib/dev"))
  (ant Delete {:dir "lib"})
  (ant Move {:file "build/lib" :tofile "lib" :verbose true :fail-on-error false})
  (pom project))
