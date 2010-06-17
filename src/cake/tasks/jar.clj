(ns cake.tasks.jar
  (:use cake cake.ant
        [clojure.useful :only [absorb]])
  (:import [org.apache.tools.ant.taskdefs Jar War Copy]
           [org.apache.tools.ant.taskdefs.optional.ssh Scp]
           [org.apache.tools.ant.types FileSet ZipFileSet]
           [org.codehaus.plexus.logging.console ConsoleLogger]
           [org.apache.maven.plugins.shade DefaultShader]
           [org.apache.maven.plugins.shade.resource ComponentsXmlResourceTransformer]
           [java.io File]))

(defn jarfile [project]
  (file (format "%s-%s.jar" (:artifact-id project) (:version project))))

(defn manifest [project]
  (merge (:manifest project)
         {"Created-By" "cake"
          "Built-By"   (System/getProperty "user.name")
          "Build-Jdk"  (System/getProperty "java.version")
          "Main-Class" (absorb (:main project) (.replaceAll "-" "_"))}))

(defn add-file-mappings [task mappings]
  (doseq [mapping mappings]
    (cond (vector? mapping)
          (add-zipfileset task {:file (first mapping) :fullpath (second mapping)})

          (string? mapping)
          (let [file   (File. mapping)    ;;more complex than it should be to allow mappings to jar/war root
                name   (.getName file)
                parent (.getParent file)
                dir    (or parent ".")
                parent (or parent "")]
            (add-zipfileset task {:dir dir :prefix parent :includes name}))

          (map? mapping)
          (add-zipfileset task mapping))))

(defn jar [project]
  (let [maven (format "META-INF/maven/%s/%s" (:group-id project) (:artifact-id project))
        cake  (format "META-INF/cake/%s/%s"  (:group-id project) (:artifact-id project))
        src   (file "src" "clj") 
        src   (if (.exists src) src (file "src"))]
    (ant Jar {:dest-file (jarfile project)}
         (add-manifest (manifest project))
         (add-zipfileset {:dir (file) :prefix maven :includes "pom.xml"})
         (add-zipfileset {:dir (file) :prefix cake  :includes "*.clj"})
         (add-fileset    {:dir (file "classes")     :includes "**/*.class"})
         (add-fileset    {:dir src                  :includes "**/*.clj"})
         (add-fileset    {:dir (file "src" "jvm")   :includes "**/*.java"})
         (add-file-mappings (:jar-files project)))))

(deftask jar => compile
  "Build a jar file containing project source and class files."
  (jar project))

(defn uberjarfile [project]
  (file (format "%s-%s-standalone.jar" (:artifact-id project) (:version project))))

(defn jars [project]
  (into #{(jarfile project)}
    (filter #(.endsWith (.getName %) ".jar")
      (file-seq (file "lib")))))

(defn rebuild-uberjar? [jarfile jars]
  (let [last-mod (.lastModified jarfile)]
    (some #(< last-mod (.lastModified %)) jars)))

(defn uberjar [project]
  (let [jars     (jars project)
        jarfile  (uberjarfile project)]
    (when (rebuild-uberjar? jarfile jars)
      (log "Building jar: " jarfile)
      (doto (DefaultShader.)
        (.enableLogging (ConsoleLogger. ConsoleLogger/LEVEL_WARN "uberjar"))
        (.shade jars jarfile [] [] [(ComponentsXmlResourceTransformer.)])))))

(deftask uberjar => jar
  "Create a standalone jar containing all project dependencies."
  (uberjar project))

(defn warfile [project]
  (file (format "%s-%s.war" (:artifact-id project) (:version project))))

(defn war [project]
  (let [web     "WEB-INF"
        classes (str web "/classes")]
    (ant War {:dest-file (warfile project)}
         (add-zipfileset {:dir (file "src")       :prefix web     :includes "*web.xml"})
         (add-zipfileset {:dir (file "classes")   :prefix classes :includes "**/*.class"})
         (add-zipfileset {:dir (file "resources") :prefix classes :includes "*"})
         (add-fileset    {:dir (file "src" "html")})
         (add-file-mappings (:war-files project)))))

(deftask war => compile
  "Create a web archive containing project source and class files."
  (war project))

(defn uberwar [project]
  (ant War {:dest-file (warfile project) :update true}
       (add-zipfileset {:dir (file "lib") :prefix "WEB-INF/lib" :includes "*.jar"})))

(deftask uberwar => war
  "Create a web archive containing all project dependencies."
  (uberwar project))

(defn keyfile [files]
  (let [sshdir (File. (System/getProperty "user.home") ".ssh")]
    (first
      (filter #(.exists %)
        (map #(File. sshdir %) files)))))

(defn release [jar]
  (log "Releasing to clojars: " jar)
  (ant Scp {:todir "clojars@clojars.org:" :trust true :keyfile (keyfile ["id_rsa" "id_dsa" "identity"])}
       (add-fileset {:file (file "pom.xml")})
       (add-fileset {:file jar})))

(deftask release => jar
  "Release project jar to clojars."
  (release (jarfile project)))