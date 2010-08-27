(ns cake.tasks.dependencies
  (:use cake cake.core cake.ant
        [cake.project :only [group]]
        [clojure.java.shell :only [sh]])
  (:import [org.apache.maven.artifact.ant DependenciesTask RemoteRepository WritePomTask Pom]
           [org.apache.tools.ant.taskdefs Copy Delete ExecTask Move]
           [org.apache.maven.model Dependency Exclusion License]
           [java.io File]))

(def *exclusions* nil)

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

(defn add-license [task attrs]
  (when attrs
    (.addConfiguredLicense task
      (make License attrs))))

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
         :exclusions  (map exclusion (concat *exclusions* (:exclusions opts)))}))))

(defn subproject-path [dep]
  (when *config*
    (*config* (str "subproject." (name dep)))))

(defn add-jarset [task path exclusions]
  (let [exclusions (map #(re-pattern (str % "-\\d.*")) exclusions)]
    (doseq [jar (fileset-seq {:dir path :includes "*.jar"}) :let [name (.getName jar)]]
      (when (not-any? #(re-matches % name) exclusions)
        (add-fileset task {:file jar})))))

(defn glob [dir pattern]
  (let [pattern (re-pattern pattern)
        match?  #(re-matches pattern (.getName %))]
    (filter match? (.listFiles (File. dir)))))

(defn install-subprojects []
  (seq (doall
    (for [type [:dependencies :dev-dependencies]
          [dep _ & opts] (*project* type)
          :let [opts (apply array-map opts), path (subproject-path dep)] :when path]
      (binding [cake/*root* path]
        (cake-exec "install" "--clean"))))))

(defn extract-native [jars dest]
  (doseq [jar jars]
    (ant Copy {:todir dest :flatten true}
         (add-zipfileset {:src jar :includes (format "native/%s/%s/*" (os-name) (os-arch))}))))

(defn fetch [deps dest]
  (when (seq deps)
    (let [ref-id (str "cake.deps.fileset." (.getName dest))]
      (ant DependenciesTask {:fileset-id ref-id :path-id (:name *project*)}
           (add-repositories (into repositories (:repositories *project*)))
           (add-dependencies deps))
      (ant Copy {:todir dest :flatten true}
           (.addFileset (get-reference ref-id)))))
  (extract-native
   (fileset-seq {:dir dest :includes "*.jar"})
   (str dest "/native")))

(defn make-pom []
  (let [refid "cake.pom"
        file  (file "pom.xml")
        attrs (select-keys *project* [:artifact-id :group-id :version :name :description])]
    (ant Pom (assoc attrs :id refid)
      (add-license (:license *project*))
      (add-dependencies (:dependencies *project*)))
    (ant WritePomTask {:pom-ref-id refid :file file})))

(defn fetch-deps []
  (log "Fetching dependencies...")
  (fetch (:dependencies *project*) (file "build/lib"))
  (binding [*exclusions* ['clojure 'clojure-contrib]]
    (fetch (:dev-dependencies *project*) (file "build/lib/dev")))
  (when (.exists (file "build/lib"))
    (ant Delete {:dir "lib"})
    (ant Move {:file "build/lib" :tofile "lib" :verbose true}))
  (invoke clean {})
  (bake-restart))

(defn stale-deps? [deps-str deps-file]
  (or (not (.exists deps-file)) (not= deps-str (slurp deps-file))))

(deftask pom "Generate pom.xml from project.clj."
  (make-pom))

(deftask deps #{pom}
  "Fetch dependencies and dev-dependencies. Use 'cake deps force' to refetch."
  (let [deps-str  (prn-str (into (sorted-map) (select-keys *project* [:dependencies :dev-dependencies])))
        deps-file (file "lib" "deps.clj")]
    (if (or (install-subprojects) (stale-deps? deps-str deps-file) (= ["force"] (:deps *opts*)))
      (do (fetch-deps)
          (spit deps-file deps-str))
      (when (= ["force"] (:compile *opts*))
        (invoke clean {})))))