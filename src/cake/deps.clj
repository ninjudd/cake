(ns cake.deps
  (:use cake uncle.core
        [cake.file :only [with-root file-exists? rmdir mv file]]
        [cake.utils :only [cake-exec]]
        [bake.core :only [log os-name os-arch]]
        [clojure.java.shell :only [sh]]
        [clojure.string :only [split]]
        [useful :only [map-to]])
  (:require depot.maven
            [depot.deps :as depot])
  (:import [org.apache.tools.ant.taskdefs Copy Delete]))

(def default-repos
  [["clojure"           "http://build.clojure.org/releases"]
   ["clojure-snapshots" "http://build.clojure.org/snapshots"]
   ["clojars"           "http://clojars.org/repo"]
   ["maven"             "http://repo1.maven.org/maven2"]])

(def dep-types [:dependencies :dev-dependencies :ext-dependencies])

(def dep-dest
  {:dependencies     "lib"
   :dev-dependencies "lib/dev"
   :ext-dependencies "lib/ext"})

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

(defn extract-native! [deps]
  (doseq [jars (vals deps)
          jar  jars]
    (ant Copy {:todir (file "lib" "native") :flatten true}
      (add-zipfileset {:src jar :includes (format "native/%s/%s/*" (os-name) (os-arch))}))
    (ant Copy {:todir (file "lib" "native") :flatten true}
      (add-zipfileset {:src jar :includes "lib/*.jar" }))))

(defn fetch-deps [type]
  (binding [depot/*repositories* default-repos
            depot/*exclusions*   (when (= :dev-dependencies type)
                                   '[org.clojure/clojure org.clojure/clojure-contrib])]
    (depot/fetch-deps *project* type)))

(defn deps [type]
  (get @dep-jars type))

(defn copy-deps! [deps]
  (when (and (:copy-deps *project*)
             (or *overwrite* (not (file-exists? "lib"))))
    (doseq [[type jars] deps :when jars]
      (ant Copy {:todir (file "build" (dep-dest type)) :flatten true}
        (.addFileset (:fileset (meta jars)))))
    (rmdir "lib")
    (mv (file "build" "lib") "lib")))

(defn fetch-deps! []
  (install-subprojects!)
  (let [deps (map-to fetch-deps dep-types)]
    (copy-deps! deps)
    (extract-native! deps)
    (reset! dep-jars deps)))

(defn clear-deps! []
  (doseq [type dep-types]
    (depot/clear-deps *project* type)))
