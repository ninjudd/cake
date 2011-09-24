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

(defn deps-cache [context]
  (file (mkdir "lib" context)
        "deps.cache"))

(defn- jar-name [jar]
  (if (:long *opts*)
    (.getPath (file jar))
    (.getName (file jar))))

(defn print-deps [context]
  (println)
  (when-let [jars (seq (deps context))]
    (println (str (name type) ":"))
    (doseq [jar (sort (map jar-name jars))]
      (println " " jar))
    (println)))

(defn fetch-deps [context]
  (binding [depot/*repositories* default-repos]
    (try (depot/fetch-deps *project* context)
         (catch org.apache.tools.ant.BuildException e
           (println "\nUnable to resolve the following dependencies:\n")
           (doall (map println (filter (partial re-matches #"\d+\) .*")
                                       (split (.getMessage e) #"\n"))))
           (println)))))

(defn copy-deps [context dest]
  (let [build (file "build" "deps")]
    (when-let [jars (fetch-deps context)]
      (ant Copy {:todir (file build context) :flatten true}
        (.addFileset (:fileset (meta jars)))))
    (rmdir dest)
    (mv build dest)
    (fileset-seq {:dir dest :includes "*.jar"})))

(defn fetch-deps! [context & {force? :force}]
  (let [cache       (deps-cache context)
        [deps jars] (when (and (not force?) (file-exists? cache))
                      (read-string (slurp cache)))]
    (if (= deps (:dependencies *project*))
      (do (swap! dep-jars assoc context jars)
          true)
      (let [lib (parent cache)]
        (install-subprojects!)
        (log :deps "Fetching dependencies...")
        (spit cache
              (pr-str
               [(:dependencies *project*)
                (swap! dep-jars assoc context
                       (vec (map (memfn getPath)
                                 (if (:copy-deps *project*)
                                   (copy-deps context lib)
                                   (fetch-deps context)))))]))
        (extract-native! lib)
        false))))

(defn deps [context]
  (or (get @dep-jars context)
      (do (fetch-deps! context)
          (get @dep-jars context))))


