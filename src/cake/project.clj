(ns cake.project
  (:use cake classlojure
        [bake.io :only [get-outs]]
        [cake.file :only [file]]
        [cake.ant :only [fileset-seq]]
        [clojure.string :only [join]]
        [cake.utils.useful :only [assoc-or update merge-in tap]])
  (:import [java.io File]))

(def global-root (.getPath (File. (System/getProperty "user.home") ".cake")))

(defn classpath []
  (map #(str "file:" (.getPath %) (if (.isDirectory %) "/" ""))
       (concat (map file [(System/getProperty "bake.path")
                          "src/" "src/clj/" "classes/" "resources/" "dev/" "test/" "test/classes/"])
               (fileset-seq {:dir "lib"     :includes "*.jar"})
               (fileset-seq {:dir "lib/dev" :includes "*.jar"})
               (fileset-seq {:dir (str global-root "/lib/dev") :includes "*.jar"}))))

(defonce classloader nil)

(defn reload! []
  (alter-var-root #'classloader
    (fn [_]
      (let [cl (classlojure (classpath))]
        (eval-in cl '(do (require 'cake)
                         (require 'bake.io)
                         (require 'clojure.main)
                         (bake.io/init-multi-out)))
        cl))))

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
            (if (.getClassLoader (class val))
              (update b 0 conj  sym val)
              (update b 1 assoc sym val)))
          [[] {}]
          (partition 2 bindings)))

(defn- shared-bindings []
  `[~'cake/*current-task* '~*current-task*
    ~'cake/*project*      '~*project*
    ~'cake/*context*      '~*context*
    ~'cake/*script*       '~*script*
    ~'cake/*opts*         '~*opts*
    ~'cake/*pwd*          '~*pwd*
    ~'cake/*env*          '~*env*
    ~'cake/*vars*         '~*vars*])

(defn project-eval [ns-forms bindings body]
  (let [[let-bindings object-bindings] (separate-bindings bindings)
        temp-ns (gensym "bake")
        form
        `(do (binding [*ns* nil]
               (ns ~temp-ns
                 (:use ~'cake)
                 ~@ns-forms))
             (fn [ins# outs# ~@(keys object-bindings)]
               (clojure.main/with-bindings
                 (bake.io/with-streams ins# outs#
                   (binding ~(shared-bindings)
                     (let ~(quote-if odd? let-bindings)
                       (in-ns '~temp-ns)
                       (try ~@body
                            (finally (remove-ns '~temp-ns)))))))))]
    (spit "/tmp/swank" (pr-str form))
    (try (apply eval-in classloader form *ins* (get-outs *outs*) (vals object-bindings))
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

(defn files [local-files global-files]
  (into (map #(File. %) local-files)
        (map #(File. global-root %) global-files)))

(defn group [project]
  (if (or (= project 'clojure) (= project 'clojure-contrib))
    "org.clojure"
    (or (namespace project) (name project))))

(defn dep-map [deps]
  (into {}
    (for [[dep version & opts] deps]
      [dep (apply hash-map :version version opts)])))

(defn create [project-name version opts]
  (let [artifact (name project-name)]
    (-> opts
        (assoc :artifact-id  artifact
               :group-id     (group project-name)
               :aot          (or (:aot opts) (:namespaces opts))
               :version      version
               :context      (symbol (or (:context opts) "dev")))
        (update :dependencies     dep-map)
        (update :dev-dependencies dep-map)
        (assoc-or :name artifact))))
