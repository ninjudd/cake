(ns cake.classloader
  (:use cake
        [classlojure :exclude [with-classloader]]
        [classlojure :only [printable?]]
        [cake.deps :only [deps]]
        [bake.core :only [debug?]]
        [cake.file :only [file global-file path-string]]
        [uncle.core :only [fileset-seq]]
        [clojure.string :only [split join]]
        [clojure.pprint :only [pprint]])
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
  (let [ext-deps     (deps :ext-dependencies)
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

(defn append-plugin-dependencies! []
  (apply append-classpath! base-classloader
         (mapcat to-urls (deps :plugin-dependencies))))

(defn reset-classloader! []
  (alter-var-root #'*classloader*
    (fn [cl]
      (when cl (eval-in cl '(shutdown-agents)))
      (when-let [classloader (make-classloader)]
        (set-classpath! classloader)
        classloader))))

(defn reset-test-classloader! []
  (alter-var-root #'test-classloader
    (constantly (make-classloader (deps :test-dependencies)))))

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
                     `(remove-ns '~*bake-ns*)))))))

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

(defn bake-eval
  "Evaluate the given form in the project classloader. The form is expected to return a function
  which is then invoked on the provided arguments."
  [form & args]
  (let [named-args (for [arg args]
                     (if (printable? arg)
                       [(list 'quote arg)]
                       [(gensym "arg") arg]))
        core-args (filter #(= 2 (count %)) named-args)
        form `(do (in-ns '~*bake-ns*)
                  (fn [ins# outs# ~@(map first core-args)]
                    (clojure.main/with-bindings
                      (bake.io/with-streams ins# outs#
                        (binding [~@(shared-bindings)]
                          (apply ~form ~(vec (map first named-args))))))))]
    (apply eval-in *classloader*
           `(clojure.main/with-bindings (eval '~form))
           *ins* *outs* (map second core-args))))

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
