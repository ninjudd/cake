(ns cake.deps
  (:use cake uncle.core
        [cake.file :only [with-root file-exists? rmdir mv file parent mkdir]]
        [cake.utils :only [cake-exec]]
        [bake.core :only [log os-name os-arch]]
        [clojure.java.shell :only [sh]]
        [clojure.string :only [split join]]
        [useful.utils :only [defm]]
        [useful.map :only [into-map map-to map-vals]]
        [useful.fn :only [all any !]])
  (:require depot.maven
            [depot.deps :as depot])
  (:import [org.apache.tools.ant.taskdefs Copy Delete]))

(def default-repos
  [["clojure"           "http://build.clojure.org/releases"]
   ["clojure-snapshots" "http://build.clojure.org/snapshots"]
   ["clojars"           "http://clojars.org/repo"]
   ["maven"             "http://repo1.maven.org/maven2"]])

(defn subproject-path [dep]
  (when *config*
    (or (get *config* (str "subproject." (namespace dep) "." (name dep)))
        (get *config* (str "subproject." (name dep))))))

(defn install-subprojects! []
  (doseq [dep (keys (:dependencies *project*))]
    (when-let [path (subproject-path dep)]
      (with-root path
        (cake-exec "install")))))

(defn extract-native! [dest]
  (doseq [jars (vals @dep-jars), jar jars]
    (ant Copy {:todir dest}
      (add-zipfileset {:src jar :includes "native/**"}))
    (ant Copy {:todir dest :flatten true}
      (add-zipfileset {:src jar :includes "lib/*.jar" }))))

(defn fetch-deps [type]
  (binding [depot/*repositories* default-repos]
    (try (depot/fetch-deps *project*)
         (catch org.apache.tools.ant.BuildException e
           (println "\nUnable to resolve the following dependencies:\n")
           (doall (map println (filter (partial re-matches #"\d+\) .*")
                                       (split (.getMessage e) #"\n"))))
           (println)))))

(defn deps-cache []
  (file (mkdir (first (:library-path *project*))
               (name (:context *project*)))
        "deps.cache"))

(defn- jar-name [jar]
  (if (:long *opts*)
    (.getPath (file jar))
    (.getName (file jar))))

(defn print-deps []
  (println)
  (doseq [type (keys dep-types)]
    (when-let [jars (seq (deps type))]
      (println (str (name type) ":"))
      (doseq [jar (sort (map jar-name jars))]
        (println " " jar))
      (println))))

(let [subdir {:dependencies         ""
              :dev-dependencies     "dev"
              :ext-dependencies     "ext"
              :ext-dev-dependencies "ext/dev"
              :test-dependencies    "test"
              :plugin-dependencies  "plugin"}]

  (defn copy-deps [dest]
    (let [build (file "build" "deps")
          dep-types (keys dep-types)]
      (doseq [type dep-types]
        (when-let [jars (fetch-deps type)]
          (ant Copy {:todir (file build (subdir type)) :flatten true}
            (.addFileset (:fileset (meta jars))))))
      (rmdir dest)
      (mv build dest)
      (map-to #(fileset-seq {:dir (file dest (subdir %)) :includes "*.jar"})
              dep-types))))

(defn fetch-deps! [& {force? :force}]
  (let [cache       (deps-cache)
        [deps jars] (when (and (not force?) (file-exists? cache))
                      (read-string (slurp cache)))]
    (if (= deps (:dependencies *project*))
      (do (reset! dep-jars jars)
          true)
      (let [lib (parent cache)]
        (install-subprojects!)
        (log :deps "Fetching dependencies...")
        (spit cache
              (pr-str
               [(:dependencies *project*)
                (reset! dep-jars
                        (map-vals
                         (if (:copy-deps *project*)
                           (copy-deps lib)
                           (map-to fetch-deps (keys dep-types)))
                         #(vec (map (memfn getPath) %))))]))
        (extract-native! lib)
        false))))

(defn deps []
  (let [context (current-context)]
    (or (get @dep-jars context)
        (do (fetch-deps!)
            (get @dep-jars context)))))


