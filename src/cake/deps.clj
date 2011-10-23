(ns cake.deps
  (:use cake uncle.core
        [cake.file :only [with-root file-exists? rmdir mv file parent mkdir]]
        [cake.utils :only [cake-exec]]
        [bake.core :only [log os-name os-arch]]
        [clojure.java.shell :only [sh]]
        [clojure.string :only [split join]]
        [useful.map :only [into-map map-to map-vals]]
        [useful.fn :only [all any !]]
        [useful.experimental :only [cond-let]])
  (:require depot.maven
            [depot.deps :as depot])
  (:import [org.apache.tools.ant.taskdefs Copy Delete]))

(def default-repos
  [["clojure"           "http://build.clojure.org/releases"]
   ["clojure-snapshots" "http://build.clojure.org/snapshots"]
   ["clojars"           "http://clojars.org/repo"]
   ["maven"             "http://repo1.maven.org/maven2"]])

(def dep-types {:dependencies         (any :main (! (any :dev :ext :test :plugin)))
                :dev-dependencies     (all :dev (! :ext))
                :ext-dependencies     (all :ext (! :dev))
                :ext-dev-dependencies (all :dev :ext)
                :plugin-dependencies  :plugin
                :test-dependencies    :test})

(defn subproject-path [dep]
  (when *config*
    (or (get *config* (str "subproject." (namespace dep) "." (name dep))
        (get *config* (str "subproject." (name dep)))))))

(defn expand-subproject [path]
  (when path
    (if-let [default (*config* "projects.directory")]
      (file default path)
      (file path))))

(defn install-subprojects! []
  (doseq [dep (keys (:dependencies *project*))]
      (when-let [path (expand-subproject (subproject-path dep))]
        (prn path)
        (with-root path
          (cake-exec "install")))))

(defn extract-native! [dest]
  (doseq [jars (vals @dep-jars), jar jars]
    (ant Copy {:todir dest}
      (add-zipfileset {:src jar :includes "native/**"}))
    (ant Copy {:todir dest :flatten true}
      (add-zipfileset {:src jar :includes "lib/*.jar" }))))

(defn auto-exclusions [type]
  (cond (= :dev-dependencies  type) '[org.clojure/clojure org.clojure/clojure-contrib]
        (= :test-dependencies type) '[org.clojure/clojure]))

(defn fetch-deps [type]
  (binding [depot/*repositories* default-repos
            depot/*exclusions*   (auto-exclusions type)]
    (depot/fetch-deps *project* (dep-types type))))

(defn deps-cache []
  (file (mkdir "lib") "deps.cache"))

(defn deps [type]
  (get @dep-jars type))

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

(defn fetch-deps! [& opts]
  (let [opts        (into-map opts)
        cache       (deps-cache)
        [deps jars] (when (and (not (:force opts)) (file-exists? cache))
                      (read-string (slurp cache)))
        overwrite?  (= deps (:dependencies *project*))]
    (if overwrite?
      (reset! dep-jars jars)
      (let [lib (first (:library-path *project*))]
        (install-subprojects!)
        (log :deps "Fetching dependencies...")
        (try
          (spit cache
                (pr-str [(:dependencies *project*)
                         (reset! dep-jars
                                 (map-vals
                                  (if (:copy-deps *project*)
                                    (copy-deps lib)
                                    (map-to fetch-deps (keys dep-types)))
                                  #(vec (map (memfn getPath) %))))]))
          (catch RuntimeException e))
        (extract-native! lib)))
    overwrite?))
