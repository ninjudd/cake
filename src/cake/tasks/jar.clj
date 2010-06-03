(ns cake.tasks.jar
  (:use cake cake.ant
        [clojure.useful :only [absorb]])
  (:import [org.apache.tools.ant.taskdefs Jar Copy]
           [org.apache.tools.ant.taskdefs.optional.ssh Scp]
           [org.apache.tools.ant.types FileSet ZipFileSet]
           [org.codehaus.plexus.logging.console ConsoleLogger]
           [org.apache.maven.plugins.shade DefaultShader]
           [org.apache.maven.plugins.shade.resource ComponentsXmlResourceTransformer]
           ;; [org.apache.maven.plugin.war WarMojo]
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
      (add-fileset {:dir (:compile-path project)})
      (add-fileset {:dir (:source-path project)}))))

(deftask jar => compile
  (jar project))

(defn uberjarfile [project]
  (File. (:root project) (format "%s-%s-standalone.jar" (:artifact-id project) (:version project))))

(defn add-jars [task dir]
  (doseq [jar (file-seq (File. dir))]
    (when (.endsWith (.getName jar) ".jar")
      (add-zipfileset task {:src jar :excludes "project.clj META-INF/** meta-inf/**"}))))

(defn jars [project]
  (into #{(jarfile project)}
    (filter #(.endsWith (.getName %) ".jar")
      (file-seq (File. (:library-path project))))))

(defn uberjar [project]
  (let [jarfile (uberjarfile project)]
    (log "Building jar: " jarfile)
    (doto (DefaultShader.)
      (.enableLogging (ConsoleLogger. ConsoleLogger/LEVEL_WARN "foo"))
      (.shade (jars project) jarfile [] [] [(ComponentsXmlResourceTransformer.)]))
    (ant Copy {:file jarfile :tofile (File. (:root project) "cake.jar")})))

(deftask uberjar => jar
  (uberjar project))

(defn keyfile [files]
  (let [sshdir (File. (System/getProperty "user.home") ".ssh")]
    (first
      (filter #(.exists %)
        (map #(File. sshdir %) files)))))

(deftask release => jar
  (ant Scp {:todir "clojars@clojars.org:" :trust true :keyfile (keyfile ["id_rsa" "id_dsa" "identity"])}
    (add-fileset {:dir (:root project) :includes "pom.xml"})
    (add-fileset {:dir (:root project) :includes (jarfile project)})))