(ns cake.deps
  (:use cake uncle.core
        [cake.file :only [with-root file-exists? rmdir mv file]]
        [cake.utils :only [cake-exec]]
        [bake.core :only [log os-name os-arch]]
        [clojure.java.shell :only [sh]]
        [clojure.string :only [split join]]
        [useful.map :only [map-to]])
  (:require depot.maven
            [depot.deps :as depot])
  (:import [org.apache.tools.ant.taskdefs Copy Delete]))

(def default-repos
  [["clojure"           "http://build.clojure.org/releases"]
   ["clojure-snapshots" "http://build.clojure.org/snapshots"]
   ["clojars"           "http://clojars.org/repo"]
   ["maven"             "http://repo1.maven.org/maven2"]])

(def dep-types [:dependencies :dev-dependencies :ext-dependencies])
(def dep-jars (atom nil))
(def ^{:dynamic true} *overwrite* nil)

(defn subproject-path [dep]
  (when *config*
    (or (get *config* (str "subproject." (namespace dep) "." (name dep)))
        (get *config* (str "subproject." (name dep))))))

(defn install-subprojects! []
  (doseq [type dep-types
          dep  (keys (*project* type))]
    (when-let [path (subproject-path dep)]
      (with-root path
        (cake-exec "install")))))

(defn extract-native! [dest]
  (doseq [jars (vals @dep-jars), jar jars]
    (ant Copy {:todir dest :flatten true}
      (add-zipfileset {:src jar :includes (format "native/%s/%s/*" (os-name) (os-arch))}))
    (ant Copy {:todir dest :flatten true}
      (add-zipfileset {:src jar :includes "lib/*.jar" }))))

(defn fetch-deps [type]
  (binding [depot/*repositories* default-repos
            depot/*exclusions*   (when (= :dev-dependencies type)
                                   '[org.clojure/clojure org.clojure/clojure-contrib])]
    (try (depot/fetch-deps *project* type)
         (catch org.apache.tools.ant.BuildException e
           (println "\nUnable to resolve the following dependencies:\n")
           (doall (map println (filter (partial re-matches #"\d+\) .*")
                                       (split (.getMessage e) #"\n"))))
           (println)))))

(defn deps [type]
  (get @dep-jars type))

(defn- jar-name [jar]
  (if (:long *opts*)
    (.getPath jar)
    (.getName jar)))

(defn print-deps []
  (println)
  (doseq [type dep-types]
    (when-let [jars (seq (deps type))]
      (println (str (name type) ":"))
      (doseq [jar (sort (map jar-name jars))]
        (println " " jar))
      (println))))

(let [subdir {:dependencies     ""
              :dev-dependencies "dev"
              :ext-dependencies "ext"}]

  (defn copy-deps [dest]
    (let [build (file "build" "deps")]
      (when (or *overwrite*
                (not (file-exists? dest)))
        (doseq [type dep-types]
          (when-let [jars (fetch-deps type)]
            (ant Copy {:todir (file build (subdir type)) :flatten true}
              (.addFileset (:fileset (meta jars))))))
        (rmdir dest)
        (mv build dest))
      (map-to #(fileset-seq {:dir (file dest (subdir %)) :includes "*.jar"})
              dep-types))))

(defn fetch-deps! []
  (let [lib (file (first (:library-path *project*)))]
    (install-subprojects!)
    (println "Fetching dependencies...")
    (reset! dep-jars
            (if (:copy-deps *project*)
              (copy-deps lib)
              (map-to fetch-deps dep-types)))
    (extract-native! (file lib "native"))))

(defn clear-deps! []
  (doseq [type dep-types]
    (depot/clear-deps *project* type)))
