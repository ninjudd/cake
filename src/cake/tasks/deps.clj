(ns cake.tasks.deps
  (:use cake cake.core cake.file uncle.core
        [cake.utils :only [cake-exec os-name os-arch]]
        [cake.project :only [group reload reload!]]
        [bake.core :only [log]]
        [clojure.java.shell :only [sh]]
        [clojure.contrib.prxml :only [prxml *prxml-indent*]])
  (:require [clojure.string :as s])
  (:import [org.apache.maven.artifact.ant DependenciesTask RemoteRepository WritePomTask Pom]
           [org.apache.tools.ant.taskdefs Copy Delete Move]
           [org.apache.maven.model Dependency Exclusion License]
           [java.io File]))

(def *exclusions* nil)

(def repositories
  [["clojure"           "http://build.clojure.org/releases"]
   ["clojure-snapshots" "http://build.clojure.org/snapshots"]
   ["clojars"           "http://clojars.org/repo"]
   ["maven"             "http://repo1.maven.org/maven2"]])

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
  (doseq [[dep opts] deps]
    (add-dep task
      (make Dependency
        {:group-id    (group dep)
         :artifact-id (name dep)
         :version     (:version opts)
         :classifier  (:classifier opts)
         :exclusions  (map exclusion (concat *exclusions* (:exclusions opts)))}))))

(defn subproject-path [dep]
  (when *config*
    (or (get *config* (str "subproject." (group dep) "." (name dep)))
        (get *config* (str "subproject." (name dep))))))

(defn add-jarset [task path exclusions]
  (let [exclusions (map #(re-pattern (str % "-\\d.*")) exclusions)]
    (doseq [jar (fileset-seq {:dir path :includes "*.jar"}) :let [name (.getName jar)]]
      (when (not-any? #(re-matches % name) exclusions)
        (add-fileset task {:file jar})))))

(defn install-subprojects []
  (doseq [type [:dependencies :dev-dependencies], [dep opts] (*project* type)]
    (when-let [path (subproject-path dep)]
      (binding [*root* path]
        (cake-exec "install")))))

(defn extract-native [jars dest]
  (doseq [jar jars]
    (ant Copy {:todir (str dest "/native") :flatten true}
         (add-zipfileset {:src jar :includes (format "native/%s/%s/*" (os-name) (os-arch))}))
    (ant Copy {:todir dest :flatten true}
         (add-zipfileset {:src jar :includes "lib/*.jar" }))))

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
   dest))

(defn to-camel-case [str]
  (s/replace str
             #"-(\w)"
             (comp s/upper-case second)))

(defn from-camel-case [name]
  (s/replace name
             #"(?<![A-Z])[A-Z]+"
             (comp (partial str "-")
                   s/lower-case)))

(defn pomify [key]
  (->> key name to-camel-case (keyword nil)))

(defmulti prxml-tags
  (fn [tag value] (keyword "cake.tasks.deps" (name tag))))

(defmethod prxml-tags :default
  ([tag value]
     (when value
       [(pomify tag) value])))

(defmethod prxml-tags ::list
  ([tag values]
     [(pomify tag) (map (partial prxml-tags
                                 (-> tag name (s/replace #"ies$" "y") keyword))
                        values)]))

(doseq [c [::dependencies
           ::repositories]]
  (derive c ::list))

(defmethod prxml-tags ::dependency
  ([_ [dep opts]]
     [:dependency
      (map (partial apply prxml-tags)
           {:group-id    (group dep)
            :artifact-id (name dep)
            :version     (:version opts)
            :classifier  (:classifier opts)})]))

(defmethod prxml-tags ::repository
  ([_ [id url]]
     [:repository [:id id] [:url url]]))

(defmethod prxml-tags ::project
  ([tag values]
     (list
      [:decl!]
      [:project {:xmlns "http://maven.apache.org/POM/4.0.0"}
       [:modelVersion "4.0.0"]
       (map (partial apply prxml-tags)
            (select-keys values
                         (rseq
                          [:artifact-id :group-id :version :name
                           :description :license
                           :dependencies :repositories])))])))

(defn fetch-deps []
  (log "Fetching dependencies...")
  (fetch (:dependencies *project*) (file "build/lib"))
  (fetch (:ext-dependencies *project*) (file "build/lib/ext"))
  (binding [*exclusions* ['clojure 'clojure-contrib]]
    (fetch (:dev-dependencies *project*) (file "build/lib/dev")))
  (when (.exists (file "build/lib"))
    (ant Delete {:dir "lib"})
    (ant Move {:file "build/lib" :tofile "lib" :verbose true}))
  (invoke clean {}))

(defn stale-deps? [deps-str deps-file]
  (or (not (.exists deps-file)) (not= deps-str (slurp deps-file))))

(defile "pom.xml" #{"project.clj"}
  (prxml-tags :project *project*))

(deftask deps #{"pom.xml"}
  "Fetch dependencies and dev-dependencies. Use 'cake deps force' to refetch."
  (let [deps-str  (prn-str (into (sorted-map) (select-keys *project* [:dependencies :ext-dependencies :dev-dependencies])))
        deps-file (file "lib" "deps.clj")]
    (if (or (stale-deps? deps-str deps-file) (= ["force"] (:deps *opts*)))
      (do (install-subprojects)
          (fetch-deps)
          (spit deps-file deps-str)
          (reload!))
      (do (when (= ["force"] (:compile *opts*))
            (invoke clean {}))
          (if (or (:r *opts*) (:reload *opts*))
            (reload!)
            (reload))))))
