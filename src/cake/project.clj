(ns cake.project
  (:use cake classlojure
        [cake.file :only [file global-file]]
        [cake.ant :only [fileset-seq]]
        [clojure.string :only [join]]
        [cake.utils.useful :only [update merge-in tap]])
  (:import [java.io File]))

(defn- make-url [file]
  (str "file:" (.getPath file) (if (.isDirectory file) "/" "")))

(defn classpath []
  (map make-url
       (concat (map file [(System/getProperty "bake.path")
                          "src/" "src/clj/" "classes/" "resources/" "dev/" "test/" "test/classes/"])
               (fileset-seq {:dir (file "lib")            :includes "*.jar"})
               (fileset-seq {:dir (file "lib/dev")        :includes "*.jar"})
               (fileset-seq {:dir (global-file "lib/dev") :includes "*.jar"}))))

(defn ext-classpath []
  (map make-url
       (fileset-seq {:dir "lib/ext" :includes "*.jar"})))

(defonce classloader nil)

(defn make-classloader []
  (wrap-ext-classloader (ext-classpath))
  (when-let [cl (classlojure (classpath))]
    (eval-in cl '(do (require 'cake)
                     (require 'bake.io)
                     (require 'bake.reload)
                     (require 'clojure.main)))
    cl))

(defn reload! []
  (alter-var-root #'classloader (fn [_] (make-classloader))))

(defn reload []
  (alter-var-root #'classloader
    (fn [cl]
      (if cl
        (do (eval-in cl '(bake.reload/reload)) cl)
        (make-classloader)))))

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
           (throw e)))))

(defmacro bake
  "Execute code in a your project classloader. Bindings allow passing state to the project
   classloader. Namespace forms like use and require must be specified before bindings."
  {:arglists '([ns-forms* bindings body*])}
  [& forms]
  (let [[ns-forms [bindings & body]] (split-with (complement vector?) forms)]
    `(project-eval '~ns-forms ~(quote-if even? bindings) '~body)))

(defn group [project]
  (if (or (= project 'clojure) (= project 'clojure-contrib))
    "org.clojure"
    (or (namespace project) (name project))))

(defn dep-map [deps]
  (into {}
    (for [[dep version & opts] deps]
      [dep (apply hash-map :version version opts)])))

(defn create [project-name version opts]
  (let [artifact (name project-name)
        artifact-version (str artifact "-" version)]
    (-> opts
        (assoc :artifact-id      artifact
               :group-id         (group project-name)
               :version          version
               :name             (or (:name opts) artifact)
               :aot              (or (:aot opts) (:namespaces opts))
               :context          (symbol (or (:context opts) "dev"))
               :jar-name         (or (:jar-name opts) artifact-version)
               :war-name         (or (:war-name opts) artifact-version)
               :uberjar-name     (or (:uberjar-name opts) (str artifact-version "-standalone"))
               :dependencies     (dep-map (or (:dependencies     opts) (:deps     opts)))
               :dev-dependencies (dep-map (or (:dev-dependencies opts) (:dev-deps opts)))
               :ext-dependencies (dep-map (or (:ext-dependencies opts) (:ext-deps opts)))))))
