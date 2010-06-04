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
  (File. (:root project) (format "%s-%s.jar" (:artifact-id project) (:version project))))

(defn manifest [project]
  (merge (:manifest project)
         {"Created-By" "cake"
          "Built-By"   (System/getProperty "user.name")
          "Build-Jdk"  (System/getProperty "java.version")
          "Main-Class" (absorb (:main project) (.replaceAll "-" "_"))}))

(defn jar [project]
  (let [maven (format "META-INF/maven/%s/%s" (:group-id project) (:artifact-id project))
        cake  (format "META-INF/cake/%s/%s"  (:group-id project) (:artifact-id project))]
    (ant Jar {:dest-file (jarfile project)}
         (add-manifest (manifest project))
         (add-zipfileset {:dir (:root project) :prefix maven :includes "pom.xml"})
         (add-zipfileset {:dir (:root project) :prefix cake  :includes "*.clj"})
         (add-fileset    {:dir (:compile-path project)})
         (add-fileset    {:dir (:source-path project)}))))

(deftask jar => compile
  (jar project))

(defn uberjarfile [project]
  (File. (:root project) (format "%s-%s-standalone.jar" (:artifact-id project) (:version project))))

(defn jars [project]
  (into #{(jarfile project)}
    (filter #(.endsWith (.getName %) ".jar")
      (file-seq (File. (:library-path project))))))

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
  (uberjar project))

(defn warfile [project]
  (File. (:root project) (format "%s-%s.war" (:artifact-id project) (:version project))))

(defn war [project]
  (let [web     "WEB-INF"
        classes (str web "/classes")]
    (ant War {:dest-file (warfile project)}
         (add-zipfileset {:dir (:source-path project)    :prefix web     :includes "*web.xml"})
         (add-zipfileset {:dir (:compile-path project)   :prefix classes :includes "*.class"})
         (add-zipfileset {:dir (:resources-path project) :prefix classes :includes "*"})
         (add-fileset    {:dir (File. (:source-path project) "html")}))))

(deftask war => compile
  (war project))

(defn uberwar [project]
  (ant War {:dest-file (warfile project) :update true}
       (add-zipfileset {:dir (:library-path project) :prefix "WEB-INF/lib" :includes "*.jar"})))

(deftask uberwar => war
  (uberwar project))

(defn keyfile [files]
  (let [sshdir (File. (System/getProperty "user.home") ".ssh")]
    (first
      (filter #(.exists %)
        (map #(File. sshdir %) files)))))

(deftask release => jar
  (ant Scp {:todir "clojars@clojars.org:" :trust true :keyfile (keyfile ["id_rsa" "id_dsa" "identity"])}
    (add-fileset {:dir (:root project) :includes "pom.xml"})
    (add-fileset {:dir (:root project) :includes (jarfile project)})))