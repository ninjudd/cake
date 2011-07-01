(ns cake.project
  (:use cake
        [cake.deps :only [deps]]
        [classlojure :only [wrap-ext-classloader classlojure eval-in get-classpath base-classloader append-classpath!]]
        [bake.core :only [debug?]]
        [cake.file :only [file global-file path-string]]
        [uncle.core :only [fileset-seq]]
        [clojure.string :only [split join trim-newline]]
        [clojure.java.shell :only [sh]]
        [useful.utils :only [adjoin]]
        [useful.map :only [update into-map]]
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
                         (get *config* "project.classpath")
                         (path-string (global-file "lib/dev/*"))]
                        paths)))

(defn ext-classpath []
  (mapcat to-urls (deps :ext-dependencies)))

(defn make-classloader [& paths]
  (when (:ext-dependencies *project*)
    (wrap-ext-classloader (ext-classpath)))
  (when-let [cl (classlojure (apply classpath paths))]
    (eval-in cl '(do (require 'cake)
                     (require 'bake.io)
                     (require 'bake.reload)
                     (require 'clojure.main)))
    cl))

(defn set-classpath!
  "Set the JVM classpath property to the current clojure classloader."
  [classloader]
  (System/setProperty "java.class.path" (join ":" (get-classpath classloader))))

(defonce *classloader* nil)
(defonce test-classloader nil)

(defn append-dev-dependencies! []
  (apply append-classpath! base-classloader
         (mapcat to-urls (deps :dev-dependencies))))

(defn reset-classloader! []
  (alter-var-root #'*classloader*
    (fn [cl]
      (when cl (eval-in cl '(shutdown-agents)))
      (when-let [classloader (make-classloader)]
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
    (eval-in *classloader* '(bake.reload/reload)))
  (when test-classloader
    (eval-in test-classloader '(bake.reload/reload))))

(defmacro with-classloader [paths & forms]
  `(binding [*classloader* (make-classloader ~@paths)]
     ~@forms))

(defmacro with-test-classloader [& forms]
  `(binding [*classloader* test-classloader]
     ~@forms))

(defn- quote-if
  "We need to quote the binding keys so they are not evaluated within the bake
   syntax-quote and the binding values so they are not evaluated in the
   project/project-eval syntax-quote. This function makes that possible."
  [pred bindings]
  (reduce
   (fn [v form]
     (if (pred (count v))
       (conj v (list 'quote form))
       (conj v form)))
   [] bindings))

(defn- separate-bindings
  "Separate bindings based on whether their value is a Java core type or not, because Java types
   should be passed directly to the project classloader, while other values should be serialized."
  [bindings]
  (reduce (fn [b [sym val]]
            (if (and (class val) (.getClassLoader (class val)))
              (update b 0 conj  sym val)
              (update b 1 assoc sym val)))
          [[] {}]
          (partition 2 bindings)))

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

;; TODO: this function is insane. make it sane.
(defn project-eval [ns-forms bindings body]
  (let [[let-bindings object-bindings] (separate-bindings bindings)
        temp-ns (gensym "bake")
        form
        `(do (ns ~temp-ns
               (:use ~'cake)
               ~@ns-forms)
             (fn [ins# outs# ~@(keys object-bindings)]
               (try
                 (clojure.main/with-bindings
                   (bake.io/with-streams ins# outs#
                     (binding ~(shared-bindings)
                       (let ~(quote-if odd? let-bindings)
                         ~@body))))
                 (finally
                  (remove-ns '~temp-ns)))))]
    (try (apply eval-in *classloader*
                `(clojure.main/with-bindings (eval '~form))
                *ins* *outs* (vals object-bindings))
         (catch Throwable e
           (println "error evaluating:")
           (prn body)
           (throw e)))))

(defmacro bake
  "Execute code in a your project classloader. Bindings allow passing state to the project
   classloader. Namespace forms like use and require must be specified before bindings."
  {:arglists '([ns-forms* bindings body*])}
  [& forms]
  (let [[ns-forms [bindings & body]] (split-with (complement vector?) forms)]
    `(project-eval '~ns-forms ~(quote-if even? bindings) '~body)))

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
                                 (assoc :version version)
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

(defn create [project-name opts]
  (let [base-version (:version opts)
        version (trim-newline
                 (if (string? base-version)
                   base-version
                   (get-version base-version)))
        artifact (name project-name)
        artifact-version (str artifact "-" version)]
    (-> opts
        (assoc :artifact-id       artifact
               :group-id          (group project-name)
               :version           version
               :name              (or (:name opts) artifact)
               :aot               (or (:aot opts) (:namespaces opts))
               :context           (symbol (or (get *config* "project.context") (:context opts) "dev"))
               :jar-name          (or (:jar-name opts) artifact-version)
               :war-name          (or (:war-name opts) artifact-version)
               :uberjar-name      (or (:uberjar-name opts) (str artifact-version "-standalone"))
               :dev-dependencies  (dep-map (concat (:dev-dependencies    opts) (:dev-deps    opts)))
               :ext-dependencies  (dep-map (concat (:ext-dependencies    opts) (:ext-deps    opts)))
               :test-dependencies (dep-map (concat (:test-dependencies   opts) (:test-deps   opts)))
               :dependencies      (dep-map (concat (:dependencies        opts) (:deps        opts)
                                                   (:native-dependencies opts) (:native-deps opts))))
        (assoc-path :source-path        "src")
        (assoc-path :test-path          "test")
        (assoc-path :resources-path     "resources")
        (assoc-path :library-path       "lib")
        (assoc-path :dev-resources-path "dev")
        (assoc-path :compile-path       "classes")
        (assoc-path :test-compile-path  :test-path "classes"))))
