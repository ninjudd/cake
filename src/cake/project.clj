(ns cake.project
  (:use cake classlojure
        [bake.core :only [debug?]]
        [cake.file :only [file global-file]]
        [uncle.core :only [fileset-seq]]
        [clojure.string :only [split join trim-newline]]
        [clojure.java.shell :only [sh]]
        [cake.utils.useful :only [update merge-in into-map absorb as-vec]]
        [clojure.java.io :only [reader]])
  (:import [java.io File]))

(defn- make-url [file]
  (str "file:" (.getPath file) (if (.isDirectory file) "/" "")))

(defn- path-files [paths]
  (mapcat (fn [path]
            (if-let [[_ path] (re-matches #"(.*)/\*" path)]
              (fileset-seq {:dir path :includes "*.jar"})
              [(file path)]))
          (absorb paths (split (re-pattern File/pathSeparator)))))

(defn classpath []
  (map make-url
       (concat (map file
                    (flatten [(System/getProperty "bake.path")
                              (or (:source-path *project*)
                                  ["src/" "src/clj/"])
                              (:java-source-path *project*)
                              (:test-path *project*)
                              (map #(str (file %) "classes")
                                   (as-vec (:test-path *project*)))
                              (:resources-path *project*)
                              (:dev-resources-path *project*)
                              "classes/"
                              (:extra-classpath-dirs *project*)]))
               (path-files (get *config* "project.classpath"))
               (fileset-seq {:dir (file (:library-path *project*))
                             :includes "*.jar"})
               (fileset-seq {:dir (file (:library-path *project*) "dev")
                             :includes "*.jar"})
               ;; This is from the user's .cake dir. No setting for it.
               (fileset-seq {:dir (global-file "lib/dev") :includes "*.jar"}))))

(defn ext-classpath []
  (map make-url
       (fileset-seq {:dir (str (file (:library-path *project*)
                                     "ext"))
                     :includes "*.jar"})))

(defonce classloader nil)

(defn make-classloader []
  (when (:ext-dependencies *project*)
    (wrap-ext-classloader (ext-classpath)))
  (when-let [cl (classlojure (classpath))]
    (eval-in cl '(do (require 'cake)
                     (require 'bake.io)
                     (require 'bake.reload)
                     (require 'clojure.main)))
    cl))

(defn set-classpath!
  "Set the JVM classpath property to the current clojure classloader."
  [classloader]
  (System/setProperty "java.class.path"
                      (join ":" (for [url (.getURLs classloader)]
                                  (let [path (.getPath url)]
                                    (if (.endsWith path "/")
                                      (.substring path 0 (- (count path) 1))
                                      path))))))

(defn reload! []
  (alter-var-root #'classloader
    (fn [cl]
      (when cl (eval-in cl '(shutdown-agents)))
      (let [classloader (make-classloader)]
        (set-classpath! classloader)
        classloader))))

(defn reload []
  (alter-var-root #'classloader
    (fn [cl]
      (if cl
        (do (eval-in cl '(bake.reload/reload)) cl)
        (let [classloader (make-classloader)]
          (set-classpath! classloader)
          classloader)))))

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

(defn project-eval [ns-forms bindings body]
  (reload)
  (when classloader
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
      (try (apply eval-in classloader
                  `(clojure.main/with-bindings (eval '~form))
                  *ins* *outs* (vals object-bindings))
           (catch Throwable e
             (println "error evaluating:")
             (prn body)
             (throw e))))))

(defmacro bake
  "Execute code in a your project classloader. Bindings allow passing state to the project
   classloader. Namespace forms like use and require must be specified before bindings."
  {:arglists '([ns-forms* bindings body*])}
  [& forms]
  (let [[ns-forms [bindings & body]] (split-with (complement vector?) forms)]
    `(project-eval '~ns-forms ~(quote-if even? bindings) '~body)))

(defn group [project]
  (if ('#{clojure clojure-contrib} project)
    "org.clojure"
    (some #(% project) [namespace name])))

(defn dep-map [deps]
  (let [[deps default-opts] (split-with (complement keyword?) deps)]
    (into {}
          (for [[dep version & opts] deps]
            [dep (into-map :version version default-opts opts)]))))

(defmulti get-version identity)

(defmethod get-version :git [_]
  (:out (sh "git" "describe" "--tags" "--abbrev=0")))

(defmethod get-version :hg [_]
  (-> ".hgtags" reader line-seq last (.split " ") last))

(defmethod get-version :default [r]
  (println "No pre-defined get-version method for that key."))

(defn create [project-name opts]
  (let [base-version (:version opts)
        version (trim-newline
                 (if (string? base-version)
                   base-version
                   (get-version base-version)))
        artifact (name project-name)
        artifact-version (str artifact "-" version)]
    (-> opts
        (assoc :artifact-id      artifact
               :group-id         (group project-name)
               :version          version
               :name             (or (:name opts) artifact)
               :aot              (or (:aot opts) (:namespaces opts))
               :context          (symbol (or (get *config* "project.context")
                                             (:context opts)
                                             "dev"))
               ;; Note source-path can be present but nil. Need to check for nil
               ;; and check for existence of src/jvm to determine the src path.
               :source-path          (:source-path opts)
               :java-source-path     (or (:java-source-path opts) "src/jvm")
               :test-path            (or (:test-path opts) "test/")
               :resources-path       (or (:resources opts) "resources/")
               :library-path         (or (:library-path opts) "lib/")
               :dev-resources-path   (or (:dev-resources-path opts) "dev/")
               :extra-classpath-dirs (:extra-classpath-dirs opts)
               :jar-name         (or (:jar-name opts) artifact-version)
               :war-name         (or (:war-name opts) artifact-version)
               :uberjar-name     (or (:uberjar-name opts) (str artifact-version "-standalone"))
               :dependencies     (dep-map (concat (:dependencies        opts) (:deps        opts)
                                                  (:native-dependencies opts) (:native-deps opts)))
               :dev-dependencies (dep-map (concat (:dev-dependencies    opts) (:dev-deps    opts)))
               :ext-dependencies (dep-map (concat (:ext-dependencies    opts) (:ext-deps    opts)))))))
