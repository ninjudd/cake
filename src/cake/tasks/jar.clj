(ns cake.tasks.jar
  (:use cake cake.ant ordered-set
        [useful :only [absorb]])
  (:import [org.apache.tools.ant.taskdefs Jar War Copy Delete]
           [org.apache.tools.ant.taskdefs.optional.ssh Scp]
           [org.apache.tools.ant.types FileSet ZipFileSet]
           [org.codehaus.plexus.logging.console ConsoleLogger]
           [org.apache.maven.plugins.shade DefaultShader]
           [org.apache.maven.plugins.shade.resource ComponentsXmlResourceTransformer]
           [org.apache.maven.artifact.ant InstallTask Pom]
           [java.io File]))

(defn jarfile [project]
  (file (format "%s-%s.jar" (:artifact-id project) (:version project))))

(defn manifest [project]
  (merge (:manifest project)
         {"Created-By" "cake"
          "Built-By"   (System/getProperty "user.name")
          "Build-Jdk"  (System/getProperty "java.version")
          "Main-Class" (absorb (:main project) (-> str (.replaceAll "-" "_")))}))

(defn add-license [task]
  (add-zipfileset task {:file (file "LICENSE") :fullpath "META-INF/LICENSE"}))

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

(defn build-jar [project]
  (let [maven (format "META-INF/maven/%s/%s" (:group-id project) (:artifact-id project))
        cake  (format "META-INF/cake/%s/%s"  (:group-id project) (:artifact-id project))
        src   (file "src" "clj")
        src   (if (.exists src) src (file "src"))]
    (ant Jar {:dest-file (jarfile project)}
         (add-manifest (manifest project))
         (add-license)
         (add-zipfileset {:dir (file) :prefix maven :includes "pom.xml"})
         (add-zipfileset {:dir (file) :prefix cake  :includes "*.clj"})
         (add-fileset    {:dir (file "classes")     :includes "**/*.class"})
         (add-fileset    {:dir src                  :includes "**/*.clj"})
         (add-fileset    {:dir (file "src" "jvm")   :includes "**/*.java"})
         (add-zipfileset {:file (file "LICENSE") :fullpath "META-INF/LICENSE"})
         (add-file-mappings (:jar-files project)))))

(defn clean [pattern]
  (when (:clean opts)
    (ant Delete {:dir (file) :includes pattern})))

(deftask jar #{compile}
  "Build a jar file containing project source and class files."
  (clean "*.jar")
  (build-jar project))

(defn uberjarfile [project]
  (file (format "%s-%s-standalone.jar" (:artifact-id project) (:version project))))

(defn jars [project]
  (let [jar (jarfile project)]
    (into (ordered-set jar)
          (fileset-seq {:dir "lib" :includes "*.jar"}))))

(defn rebuild-uberjar? [jarfile jars]
  (let [last-mod (.lastModified jarfile)]
    (some #(< last-mod (.lastModified %)) jars)))

(defn build-uberjar [project]
  (let [jars     (jars project)
        jarfile  (uberjarfile project)]
    (when (rebuild-uberjar? jarfile jars)
      (log "Building jar:" jarfile)
      (doto (DefaultShader.)
        (.enableLogging (ConsoleLogger. ConsoleLogger/LEVEL_WARN "uberjar"))
        (.shade jars jarfile [] [] [(ComponentsXmlResourceTransformer.)])))))

(deftask uberjar #{jar}
  "Create a standalone jar containing all project dependencies."
  (build-uberjar project))

(defn warfile [project]
  (file (format "%s-%s.war" (:artifact-id project) (:version project))))

(defn build-war [project]
  (let [web     "WEB-INF"
        classes (str web "/classes")]
    (ant War {:dest-file (warfile project)}
         (add-manifest (manifest project))
         (add-license)
         (add-zipfileset {:dir (file "src")       :prefix web     :includes "*web.xml"})
         (add-zipfileset {:dir (file "classes")   :prefix classes :includes "**/*.class"})
         (add-zipfileset {:dir (file "resources") :prefix classes :includes "*"})
         (add-fileset    {:dir (file "src" "html")})
         (add-file-mappings (:war-files project)))))

(deftask war #{compile}
  "Create a web archive containing project source and class files."
  (clean "*.war")
  (build-war project))

(defn build-uberwar [project]
  (ant War {:dest-file (warfile project) :update true}
       (add-zipfileset {:dir (file "lib") :prefix "WEB-INF/lib" :includes "*.jar"})))

(deftask uberwar #{war}
  "Create a web archive containing all project dependencies."
  (build-uberwar project))

(deftask install #{jar}
  "Install jar to local repository."
  (let [refid "cake.pom"]
    (ant Pom {:file "pom.xml" :id refid})
    (ant InstallTask {:file (jarfile project) :pom-ref-id refid})))

(defn keyfile [files]
  (let [sshdir (File. (System/getProperty "user.home") ".ssh")]
    (first
      (filter #(.exists %)
        (map #(File. sshdir %) files)))))

(defn release-to-clojars [jar]
  (log "Releasing to clojars: " jar)
  (ant Scp {:todir "clojars@clojars.org:" :trust true :keyfile (keyfile ["id_rsa" "id_dsa" "identity"])}
       (add-fileset {:file (file "pom.xml")})
       (add-fileset {:file jar})))

(deftask release #{jar}
  "Release project jar to clojars."
  (release-to-clojars (jarfile project)))