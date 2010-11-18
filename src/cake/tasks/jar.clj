(ns cake.tasks.jar
  (:use cake cake.core cake.ant cake.file
        [bake.core :only [current-context log]]
        [clojure.java.io :only [copy writer]]
        [clojure.string :only [join]]
        [cake.tasks.compile :only [source-dir]]
        [cake.utils.useful :only [absorb verify]])
  (:require [clojure.xml :as xml])
  (:import [org.apache.tools.ant.taskdefs Jar War Copy Delete Chmod Mkdir]
           [org.apache.tools.ant.types FileSet ZipFileSet]
           [org.codehaus.plexus.logging.console ConsoleLogger]
           [org.apache.maven.artifact.ant InstallTask Pom]
           [java.io File FileOutputStream]
           [java.util.jar JarFile]))

(defn artifact [ext & [modifier]]
  (let [parts [(:artifact-id *project*) (:version *project*) modifier (current-context)]]
    (file (str (join "-" (remove nil? parts)) "." ext))))

(defn jarfile []
  (artifact "jar"))

(defn manifest []
  (merge (:manifest *project*)
         {"Created-By" "cake"
          "Built-By"   (System/getProperty "user.name")
          "Build-Jdk"  (System/getProperty "java.version")
          "Class-Path" (:jar-classpath *project*)
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

(defn add-source-files [task & [prefix]]
  (when-not (:omit-source *project*)
    (add-zipfileset task {:dir (source-dir)       :prefix prefix :includes "**/*.clj"})
    (add-zipfileset task {:dir (file "src" "jvm") :prefix prefix :includes "**/*.java"})))

(def cake-context
"(ns cake)

(defn- merge-in [left right]
  (if (associative? left)
    (merge-with merge-in left right)
    right))

(def *context* '%s)

(def *project*
  (let [project '%s
        context (symbol (or (System/getProperty \"clojure.context\")
                            (System/getenv \"CLOJURE_CONTEXT\")
                            (:context project)))]
    (merge-in project (assoc (context *context*)
                        :context context))))")

(defn build-context []
  (when-not (= "cake" (:artifact-id *project*))
    (mkdir (file "build" "jar"))
    (with-open [cake-clj (writer (file "build" "jar" "cake.clj"))]
      (copy (format cake-context
                    (pr-str *context*)
                    (pr-str (.getRoot #'*project*)))
            cake-clj))))

(defn build-jar []
  (let [maven (format "META-INF/maven/%s/%s" (:group-id *project*) (:artifact-id *project*))
        cake  (format "META-INF/cake/%s/%s"  (:group-id *project*) (:artifact-id *project*))]
    (build-context)
    (ant Jar {:dest-file (jarfile)}
         (add-manifest (manifest))
         (add-license)
         (add-source-files)
         (add-zipfileset {:dir (file) :prefix maven :includes "pom.xml"})
         (add-zipfileset {:dir (file) :prefix cake  :includes "*.clj"})
         (add-fileset    {:dir (file "classes")     :includes "**/*.class"})
         (add-fileset    {:dir (file "build" "jar")})
         (add-fileset    {:dir (file "resources")})
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
  (artifact "jar" "standalone"))

(defn jars [& opts]
  (let [opts (apply hash-map opts)
        jar  (jarfile)]
    (into [jar]
          (fileset-seq {:dir "lib" :includes "*.jar" :excludes (join "," (:excludes opts))}))))

(defn plexus-components [jar]
  (let [jarfile (JarFile. jar)]
    (if-let [entry (.getEntry jarfile "META-INF/plexus/components.xml")]
      (->> entry (.getInputStream jarfile)
           xml/parse :content (filter #(= :components (:tag %))) first :content))))

(defn merge-plexus-components [jars dest]
  (when-let [components (seq (mapcat plexus-components jars))]
    (.mkdirs (.getParentFile dest))
    (with-open [file (writer dest)]
      (binding [*out* file]
        (xml/emit {:tag "component-set" :content [{:tag "components" :content components}]})
        (flush)))))

(defn add-jars [task jars]
  (doseq [jar jars :let [name (.replace (.getName jar) ".jar" "")]]
    (add-zipfileset task {:src jar :excludes "META-INF/**/*"})
    (add-zipfileset task {:src jar :includes "META-INF/**/*" :prefix (str "META-INF/" name)})))

(defn build-uberjar [jars]
  (let [plexus-components (file "build/uberjar/META-INF/plexus/components.xml")]
    (merge-plexus-components jars plexus-components)
    (ant Jar {:dest-file (uberjarfile) :duplicate "preserve"}
         (add-manifest (manifest))
         (add-jars jars)
         (add-fileset {:dir (file "build" "uberjar")}))))

(deftask uberjar #{jar}
  "Create a standalone jar containing all project dependencies."
  (build-uberjar (jars)))

(deftask bin #{uberjar}
  "Create a standalone console executable for your project."
  "Add :main to your project.clj to specify the namespace that contains your -main function."
  (if (:main *project*)
    (let [binfile (file (:artifact-id *project*))
          uberjar (uberjarfile)]
      (when (newer? uberjar binfile)
        (log "Creating standalone executable:" (.getPath binfile))
        (with-open [bin (FileOutputStream. binfile)]
          (let [opts (or (get *config* "project.java_opts") "")
                unix (format ":;exec java %s -jar $0 \"$@\"\n" opts)
                dos  (format "@echo off\r\njava %s -jar %%1 \"%%~f0\" %%*\r\ngoto :eof\r\n" opts)]
            (.write bin (.getBytes unix))
            (.write bin (.getBytes dos)))
          (copy uberjar bin))
        (ant Chmod {:file binfile :perm "+x"})))
    (println "Cannot create bin without :main namespace in project.clj")))

(defn warfile []
  (artifact "war"))

(defn build-war []
  (let [web     "WEB-INF"
        classes (str web "/classes")]
    (build-context)
    (ant War {:dest-file (warfile)}
         (add-manifest (manifest))
         (add-license)
         (add-source-files "WEB-INF/classes")
         (add-zipfileset {:dir (file "src")         :prefix web     :includes "*web.xml"})
         (add-zipfileset {:dir (file "classes")     :prefix classes :includes "**/*.class"})
         (add-zipfileset {:dir (file "resources")   :prefix classes :includes "*"})
         (add-zipfileset {:dir (file "build" "jar") :prefix classes})
         (add-fileset    {:dir (file "build" "war")})
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