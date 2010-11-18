(ns cake.project
  (:use cake classlojure
        [cake.file :only [file]]
        [cake.ant :only [fileset-seq]]
        [clojure.string :only [join]]
        [cake.utils.useful :only [assoc-or update merge-in tap]])
  (:import [java.io File]))

(def global-root (.getPath (File. (System/getProperty "user.home") ".cake")))

(defn classpath []
  (map #(str "file:" (.getPath %) (if (.isDirectory %) "/" " "))
       (concat (map file [(System/getProperty "bake.path")
                          "src/" "src/clj/" "classes/" "resources/" "dev/" "test/" "test/classes/"])
               (fileset-seq {:dir "lib"     :includes "*"})
               (fileset-seq {:dir "lib/dev" :includes "*"})
               (fileset-seq {:dir (str global-root "lib/dev") :includes "*"}))))

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

(defn project-eval [ns-forms bindings body]
  (let [form
        `(do (ns ~(symbol (str "bake.task." (name *current-task*)))
               (:use ~'cake)
               ~@ns-forms)
             (let ~(quote-if odd? bindings)
               ~@body))]
    (eval-in
     classloader
     `(fn [ins# outs#]
        (clojure.main/with-bindings
          (bake.io/with-streams ins# outs#
            (binding [~'cake/*current-task* '~*current-task*
                      ~'cake/*project*      '~*project*
                      ~'cake/*context*      '~*context*
                      ~'cake/*script*       '~*script*
                      ~'cake/*opts*         '~*opts*
                      ~'cake/*pwd*          '~*pwd*
                      ~'cake/*env*          '~*env*
                      ~'cake/*vars*         '~*vars*]
              (eval '~form)))))
     *ins* *outs*)))

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
