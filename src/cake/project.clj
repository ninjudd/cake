(ns cake.project
  (:use cake
        clojure.pprint
        [classlojure :exclude [with-classloader]]
        [cake.deps :only [deps]]
        [bake.core :only [debug?]]
        [cake.file :only [file global-file path-string]]
        [uncle.core :only [fileset-seq]]
        [clojure.string :only [split join trim-newline]]
        [clojure.java.shell :only [sh]]
        [useful.utils :only [adjoin]]
        [useful.map :only [update into-map map-vals]]
        [useful.fn :only [given]]
        [clojure.java.io :only [reader]])
  (:import [java.io File]))

(defn- path-file [path]
  (if-let [[_ dir] (and (string? path) (re-matches #"(.*)/\*" path))]
    (fileset-seq {:dir dir :includes "*.jar"})
    [(file path)]))

(defn- path-files [path]
  (when path
    (cond (string?     path) (mapcat path-file (split path (re-pattern File/pathSeparator)))
          (sequential? path) (mapcat path-file path)
          :else              (path-file path))))

(defn- to-urls [path]
  (map (fn [file]
         (str "file:" (.getPath file)
              (if (.isDirectory file) "/" "")))
       (path-files path)))

(defn classpath [& paths]
  (mapcat to-urls (into [(System/getProperty "bake.path")
                         (mapcat *project* [:source-path :test-path
                                            :resources-path :dev-resources-path
                                            :compile-path :test-compile-path])
                         (deps :dependencies)
                         (deps :dev-dependencies)
                         (map #(str % "/*") (:library-path *project*))
                         (get *config* "project.classpath")
                         (path-string (global-file "lib/dev/*"))]
                        paths)))

(defn make-classloader [& paths]
  (let [ext-deps (deps :ext-dependencies)
        ext-dev-deps (deps :ext-dev-dependencies)]
    (when (or ext-deps ext-dev-deps)
      (wrap-ext-classloader (mapcat to-urls (concat ext-deps ext-dev-deps)))))
  (if-let [cl (classlojure (apply classpath paths))]
    (doto cl
      (eval-in '(do (require 'cake)
                    (require 'bake.io)
                    (require 'bake.reload)
                    (require 'clojure.main))))
    (prn paths)))

(defn set-classpath!
  "Set the JVM classpath property to the current clojure classloader."
  [classloader]
  (System/setProperty "java.class.path" (join ":" (get-classpath classloader))))

(defn append-dev-dependencies! []
  (apply append-classpath! base-classloader
         (mapcat to-urls (deps :dev-dependencies))))

(defn reset-classloader! []
  (alter-var-root #'*classloader*
    (fn [cl]
      (when cl (eval-in cl '(shutdown-agents)))
      (when-let [classloader (make-classloader)]
        (prn :classloader classloader)
        (set-classpath! classloader)
        classloader))))

(defn reset-test-classloader! []
  (alter-var-root #'test-classloader
    (fn [_] (make-classloader (deps :test-dependencies)))))

(defn reset-classloaders! []
  (reset-classloader!)
  (reset-test-classloader!))

(defn reload []
  (when *classloader*
    (try
      (eval-in *classloader* '(bake.reload/reload))
      (catch Exception _ (reset-classloader!))))
  (when test-classloader
    (try
      (eval-in test-classloader '(bake.reload/reload))
      (catch Exception _ (reset-test-classloader!)))))

(defmacro with-classloader [paths & forms]
  `(binding [*classloader* (make-classloader ~@paths)]
     ~@forms))

(defmacro with-test-classloader [& forms]
  (if (= "true" (get *config* "disable-test-classloader"))
    `(do ~@forms)
    `(binding [*classloader* test-classloader]
       ~@forms)))

(defn- shared-bindings []
  `[~'cake/*current-task* '~*current-task*
    ~'cake/*project-root* '~*project-root*
    ~'cake/*project*      '~*project*
    ~'cake/*context*      '~*context*
    ~'cake/*script*       '~*script*
    ~'cake/*opts*         '~*opts*
    ~'cake/*pwd*          '~*pwd*
    ~'cake/*env*          '~*env*
    ~'cake/*vars*         '~*vars*])

(def *bake-ns* 'user)

(defn in-bake-ns [ns-forms f]
  (if (empty? ns-forms)
    (f)
    (binding [*bake-ns* (gensym "bake")]
      (eval-in *classloader*
               `(clojure.main/with-bindings
                  (ns ~*bake-ns* (:use ~'cake)
                      ~@ns-forms)))
      (try (f)
           (finally
            (eval-in *classloader*
                     `(remove-ns ~*bake-ns*)))))))

(defn split-ns-forms [forms]
  (split-with
   (comp #{:use :require :refer-clojure :import :refer} first)
   forms))

(defmacro bake-ns
  "Create a temporary namespace in the project classloader using the provided ns forms. This namespace will be
  used for all nested bake and bake-invoke calls."
  {:arglists '([ns-forms* body*])}
  [& forms]
  (let [[ns-forms forms] (split-ns-forms forms)]
    `(in-bake-ns '~ns-forms (fn [] ~@forms))))

(defn core-java-class? [object]
  (not (and (class object) (.getClassLoader (class object)))))

(defn bake-eval
  "Evaluate the given form in the project classloader. The form is expected to return a function
  which is then invoked on the provided arguments."
  [form & args]
  (let [named-args (for [arg args]
                     (if (core-java-class? arg)
                       [(gensym "arg") arg]
                       [arg]))
        core (filter #(= 2 (count %)) named-args)]
    (apply eval-in *classloader*
           `(fn [ins# outs# ~@(map first core)]
              (clojure.main/with-bindings
                (set! *ns* (the-ns '~*bake-ns*))
                (bake.io/with-streams ins# outs#
                  (binding ~(shared-bindings)
                    (~form ~@(map first named-args))))))
           *ins* *outs* (map second core))))

(defmacro bake-invoke
  "Invoke the given function in the project classloader, passing the provided arguments to it.
  The function can be anonymous, but Note that it is not an actual closure. The only data accessible
  to the function when it is executed in the project classloader are the arguments passed."
  [form & args]
  `(bake-eval '~form ~@args))

(defmacro bake
  "Execute code in the project classloader. Bindings allow passing state to the project classloader."
  {:arglists '([ns-forms* bindings? body*])}
  [& forms]
  (let [[ns-forms forms] (split-ns-forms forms)
        [bindings forms] (if (vector? (first forms))
                           [(apply hash-map (first forms)) (rest forms)]
                           [{} forms])
        form `(fn [~@(keys bindings)] ~@forms)]
    `(in-bake-ns '~ns-forms (fn []
                            (bake-eval '~form ~@(vals bindings))))))

(defn group [dep]
  (if ('#{clojure clojure-contrib} dep)
    "org.clojure"
    (some #(% dep) [namespace name])))

(defn add-group [dep]
  (symbol (group dep) (name dep)))

(defn dep-map [deps]
  (let [[deps default-opts] (split-with (complement keyword?) deps)]
    (into {}
          (for [[dep version & opts] deps]
            [(add-group dep) (-> (adjoin (into-map default-opts) (into-map opts))
                                 (given version assoc :version version)
                                 (update :exclusions (partial map add-group)))]))))

(defmulti get-version identity)

(defmethod get-version :git [_]
  (:out (sh "git" "describe" "--tags" "--abbrev=0")))

(defmethod get-version :hg [_]
  (-> ".hgtags" reader line-seq last (.split " ") last))

(defmethod get-version :default [r]
  (println "No pre-defined get-version method for that key."))

(defn- assoc-path
  ([opts key default]
     (let [path (or (get opts key) default)]
       (assoc opts key (if (string? path)
                         [path]
                         (vec path)))))
  ([opts key base-key suffix]
     (assoc-path opts key (vec (map #(str (file % suffix))
                                    (get opts base-key))))))

(defn qualify [type deps]
  (map-vals deps #(assoc % type true)))

(defn create [project-name opts]
  (let [base-version (:version opts)
        version (trim-newline
                 (if (string? base-version)
                   base-version
                   (get-version base-version)))
        artifact (name project-name)
        artifact-version (str artifact "-" version)]
    (-> opts
        (assoc :artifact-id  artifact
               :group-id     (group project-name)
               :version      version
               :name         (or (:name opts) artifact)
               :aot          (or (:aot opts) (:namespaces opts))
               :context      (symbol (or (get *config* "project.context") (:context opts) "dev"))
               :jar-name     (or (:jar-name opts) artifact-version)
               :war-name     (or (:war-name opts) artifact-version)
               :uberjar-name (or (:uberjar-name opts) (str artifact-version "-standalone"))
               :dependencies (merge-with adjoin
                               (qualify :main (dep-map (mapcat opts [:dependencies :native-dependencies])))
                               (qualify :dev  (dep-map (:dev-dependencies  opts)))
                               (qualify :test (dep-map (:test-dependencies opts)))))
        (assoc-path :source-path        "src")
        (assoc-path :test-path          "test")
        (assoc-path :resources-path     "resources")
        (assoc-path :library-path       "lib")
        (assoc-path :dev-resources-path "dev")
        (assoc-path :compile-path       "classes")
        (assoc-path :test-compile-path  :test-path "classes")
        (given (:java-source-path opts) update :source-path conj (:java-source-path opts)))))
