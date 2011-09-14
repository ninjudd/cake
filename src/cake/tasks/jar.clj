(ns cake.tasks.jar
  (:use cake cake.core cake.file uncle.core
        [cake.deps :only [deps]]
        [bake.core :only [current-context log project-with-context]]
        [clojure.java.io :only [copy writer]]
        [clojure.string :only [join]]
        [useful.utils :only [verify]]
        [useful.map :only [into-map]])
  (:require [clojure.xml :as xml])
  (:import [org.apache.tools.ant.taskdefs Jar War Copy]
           [org.apache.tools.ant.types FileSet ZipFileSet]
           [org.codehaus.plexus.logging.console ConsoleLogger]
           [org.apache.maven.artifact.ant InstallTask Pom]
           [java.io File FileOutputStream]
           [java.util.jar JarFile]))

(defn artifact [name-key ext]
  (file (str (name-key *project*)
             (when-let [context (current-context)]
               (str "-" context))
             ext)))

(defn jarfile [] (artifact :jar-name ".jar"))

(defn manifest []
  (merge (:manifest *project*)
         {"Created-By" "cake"
          "Built-By"   (System/getProperty "user.name")
          "Build-Jdk"  (System/getProperty "java.version")
          "Class-Path" (:jar-classpath *project*)
          "Main-Class" (when-let [main (:main *project*)]
                         (-> main str (.replaceAll "-" "_")))}))

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

(defn bakepath [& opts]
  (let [bakepath (System/getProperty "bake.path")]
    (merge (into-map opts)
           (if (.endsWith bakepath ".jar")
             {:src bakepath}
             {:dir bakepath}))))

(defn add-path [task path-name & [opts]]
  (doseq [path (*project* path-name)]
    (add-zipfileset task (assoc opts :dir path))))

(defn build-context [context]
  (let [cake-clj (file "build" context "cake.clj")]
    (when (some (partial older? cake-clj) ["project.clj" "context.clj"])
      (ant Copy {:todir (parent cake-clj) :overwrite true}
        (add-zipfileset (bakepath :includes "cake.clj")))
      (replace-token cake-clj "(comment project)" (pr-str `(quote ~(project-with-context context))))
      (when (nil? context)
        (replace-token cake-clj "(comment context)" (pr-str `(quote ~*context*)))))
    cake-clj))

(defn add-source-files [task & [opts]]
  (when-not (:omit-source *project*)
    (add-path task :source-path (assoc opts :includes "**/*.clj, **/*.java")))
  (when (:bake *project*)
    (let [[file dir] (split-path (build-context (current-context)))]
      (add-zipfileset task (into-map opts {:dir dir :includes file})))
    (add-zipfileset task (bakepath opts :excludes "cake.clj"))))

(defn meta-inf [prefix]
  (format "META-INF/%s/%s/%s" prefix (:group-id *project*) (:artifact-id *project*)))

(defn add-meta-inf [task]
  (add-zipfileset task {:dir (file ".") :prefix (meta-inf "maven") :includes "pom.xml"})
  (add-zipfileset task {:dir (file ".") :prefix (meta-inf "cake")  :includes "*.clj"}))

(defn build-jar []
  (ant Jar {:dest-file (jarfile)}
    (add-manifest (manifest))
    (add-license)
    (add-meta-inf)
    (add-source-files)
    (add-path :compile-path {:includes "**/*.class"})
    (add-path :resources-path)
    (add-fileset    {:dir (file "build" "jar")})
    (add-zipfileset {:dir (file "native") :prefix "native"})
    (add-file-mappings (:jar-files *project*))))

(defn clean [pattern]
  (when (:clean *opts*)
    (rmdir "." :includes pattern)))

(deftask jar #{compile}
  "Build a jar file containing project source and class files."
  (clean "*.jar")
  (build-jar))

(defn uberjarfile [] (artifact :uberjar-name ".jar"))

(defn jars []
  (concat [(.getPath (jarfile))]
          (deps :dependencies)
          (deps :ext-dependencies)))

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

(defn add-jar-contents [task jars]
  (doseq [jar jars :let [name (.replace (.getName (file jar)) ".jar" "")]]
    (add-zipfileset task {:src jar :excludes "META-INF/**, project.clj, LICENSE"})))

(defn build-uberjar [jarfile jars]
  (let [plexus-components (file "build/uberjar/META-INF/plexus/components.xml")]
    (merge-plexus-components jars plexus-components)
    (ant Jar {:dest-file jarfile :duplicate "preserve"}
         (add-manifest (manifest))
         (add-jar-contents jars)
         (add-meta-inf)
         (add-fileset {:dir (file "build" "uberjar")}))))

(deftask uberjar #{jar}
  "Create a standalone jar containing all project dependencies."
  (build-uberjar (uberjarfile) (jars)))

(deftask bin #{uberjar}
  "Create a standalone console executable for your project."
  "Add :main to your project.clj to specify the namespace that contains your -main function."
  (if (:main *project*)
    (let [binfile (file (:artifact-id *project*))
          uberjar (uberjarfile)]
      (when (newer? uberjar binfile)
        (log "Creating standalone executable:" (.getPath binfile))
        (with-open [bin (FileOutputStream. binfile)]
          (let [opts (or (get *config* "jvm.opts") "")
                unix (format ":;exec java %s -jar $0 \"$@\"\n" opts)
                dos  (format "@echo off\r\njava %s -jar %%1 \"%%~f0\" %%*\r\ngoto :eof\r\n" opts)]
            (.write bin (.getBytes unix))
            (.write bin (.getBytes dos)))
          (copy uberjar bin))
        (chmod binfile "+x")))
    (println "Cannot create bin without :main namespace in project.clj")))

(defn warfile [] (artifact :war-name ".war"))

(defn add-web-files [task]
  (doseq [path (:source-path *project*)]
    (add-zipfileset task {:dir path :prefix "WEB-INF" :includes "*web.xml"})
    (add-fileset    task {:dir (file path "html")})))

(defn build-war []
  (ant War {:dest-file (warfile)}
    (add-manifest (manifest))
    (add-license)
    (add-source-files {:prefix "WEB-INF/classes"})
    (add-web-files)
    (add-path :compile-path   {:prefix "WEB-INF/classes" :includes "**/*.class"})
    (add-path :resources-path {:prefix "WEB-INF/classes"})
    (add-zipfileset {:dir (file "build" "jar") :prefix "WEB-INF/classes"})
    (add-fileset    {:dir (file "build" "war")})
    (add-file-mappings (:war-files *project*))))

(deftask war #{compile}
  "Create a web archive containing project source and class files."
  (clean "*.war")
  (build-war))

(deftask uberwar #{war}
  "Create a web archive containing all project dependencies."
  (let [task (ant-type War {:dest-file (warfile) :update true})]
    (doseq [jar (jars)]
      (add-zipfileset task {:file jar :prefix "WEB-INF/lib"}))
    (execute task)))
