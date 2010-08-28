(ns cake.tasks.jar
  (:use cake cake.core cake.ant ordered-set
        [useful :only [absorb]])
  (:import [org.apache.tools.ant.taskdefs Jar War Copy Delete]
           [org.apache.tools.ant.types FileSet ZipFileSet]
           [org.codehaus.plexus.logging.console ConsoleLogger]
           [org.apache.maven.plugins.shade DefaultShader]
           [org.apache.maven.plugins.shade.resource ComponentsXmlResourceTransformer]
           [org.apache.maven.artifact.ant InstallTask Pom]
           [java.io File]))

(defn jarfile []
  (file (format "%s-%s.jar" (:artifact-id *project*) (:version *project*))))

(defn manifest []
  (merge (:manifest *project*)
         {"Created-By" "cake"
          "Built-By"   (System/getProperty "user.name")
          "Build-Jdk"  (System/getProperty "java.version")
          "Main-Class" (absorb (:main *project*) (-> str (.replaceAll "-" "_")))}))

(defn add-license [task]
  (add-fileset task {:file (file "LICENSE")}))

(defn- file-mapping [from to]
  (let [from (file from)]
    (when (.exists from)
      (if (.isDirectory from)
        {:dir from :prefix to :includes "**/*"}
        {:file from :fullpath to}))))

(defn add-file-mappings [task mappings]
  (doseq [m mappings]
    (cond (map?    m) (add-zipfileset task m)
          (string? m) (add-zipfileset task (file-mapping m m))
          (vector? m) (add-zipfileset task (apply file-mapping m)))))

(defn add-source-files [task]
  (when-not (:omit-source *project*)
    (let [src (file "src" "clj")
          src (if (.exists src) src (file "src"))]
      (add-fileset task {:dir src                :includes "**/*.clj"})
      (add-fileset task {:dir (file "src" "jvm") :includes "**/*.java"}))))

(defn build-jar []
  (let [maven (format "META-INF/maven/%s/%s" (:group-id *project*) (:artifact-id *project*))
        cake  (format "META-INF/cake/%s/%s"  (:group-id *project*) (:artifact-id *project*))]
    (ant Jar {:dest-file (jarfile)}
         (add-manifest (manifest))
         (add-license)
         (add-source-files)
         (add-zipfileset {:dir (file) :prefix maven :includes "pom.xml"})
         (add-zipfileset {:dir (file) :prefix cake  :includes "*.clj"})
         (add-fileset    {:dir (file "classes")     :includes "**/*.class"})         
         (add-zipfileset {:dir (file "native") :prefix "native"})
         (add-file-mappings (:jar-files *project*)))))

(defn clean [pattern]
  (when (:clean *opts*)
    (ant Delete {:dir (file) :includes pattern})))

(deftask jar #{compile}
  "Build a jar file containing project source and class files."
  (clean "*.jar")
  (build-jar))

(defn uberjarfile []
  (file (format "%s-%s-standalone.jar" (:artifact-id *project*) (:version *project*))))

(defn jars []
  (let [jar (jarfile)]
    (into (ordered-set jar)
          (fileset-seq {:dir "lib" :includes "*.jar"}))))

(defn rebuild-uberjar? [jarfile jars]
  (let [last-mod (.lastModified jarfile)]
    (some #(< last-mod (.lastModified %)) jars)))

(defn build-uberjar []
  (let [jars    (jars)
        jarfile (uberjarfile)]
    (when (rebuild-uberjar? jarfile jars)
      (log "Building jar:" jarfile)
      (doto (DefaultShader.)
        (.enableLogging (ConsoleLogger. ConsoleLogger/LEVEL_WARN "uberjar"))
        (.shade jars jarfile [] [] [(ComponentsXmlResourceTransformer.)])))))

(deftask uberjar #{jar}
  "Create a standalone jar containing all project dependencies."
  (build-uberjar))

(defn warfile []
  (file (format "%s-%s.war" (:artifact-id *project*) (:version *project*))))

(defn build-war []
  (let [web     "WEB-INF"
        classes (str web "/classes")]
    (when-not (or (= :all (:aot *project*)) (= :partial (:war *project*)))
      (println "warning: some namespaces may not be included in your war, use ':aot :all' to include them"))
  (ant War {:dest-file (warfile)}
         (add-manifest (manifest))
         (add-license)
         (add-source-files)
         (add-zipfileset {:dir (file "src")       :prefix web     :includes "*web.xml"})
         (add-zipfileset {:dir (file "classes")   :prefix classes :includes "**/*.class"})
         (add-zipfileset {:dir (file "resources") :prefix classes :includes "*"})
         (add-fileset    {:dir (file "src" "html")})
         (add-file-mappings (:war-files *project*)))))

(deftask war #{compile}
  "Create a web archive containing project source and class files."
  (clean "*.war")
  (build-war))

(defn build-uberwar []
  (ant War {:dest-file (warfile) :update true}
       (add-zipfileset {:dir (file "lib") :prefix "WEB-INF/lib" :includes "*.jar"})))

(deftask uberwar #{war}
  "Create a web archive containing all project dependencies."
  (build-uberwar))

(deftask install #{jar}
  "Install jar to local repository."
  (ant Pom {:file "pom.xml" :id "cake.pom"})
  (ant InstallTask {:file (jarfile) :pom-ref-id "cake.pom"}))