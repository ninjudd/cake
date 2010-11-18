(ns cake.ant
  "Lancet-inspired ant helpers."
  (:use cake
        [cake.file :only [file]]
        [bake.core :only [debug?]]
        [clojure.string :only [join]])
  (:import [org.apache.tools.ant Project NoBannerLogger]
           [org.apache.tools.ant.types Path FileSet ZipFileSet EnumeratedAttribute Environment$Variable]
           [org.apache.tools.ant.taskdefs Echo Javac Manifest Manifest$Attribute]
           [java.beans Introspector]))

(def *ant-project* nil)

(defmulti coerce (fn [type val] [type (class val)]))

(defn- property-key [property]
  (keyword (.. (re-matcher #"\B([A-Z])" (.getName property))
               (replaceAll "-$1")
               toLowerCase)))

(defn- property-setters [class]
  (reduce
   (fn [map property]
     (assoc map (property-key property) (.getWriteMethod property)))
   {} (.getPropertyDescriptors (Introspector/getBeanInfo class)))  )

(defn- set-attributes! [instance attrs]
  (let [setters (property-setters (class instance))]
    (doseq [[key val] attrs]
      (if-let [setter (setters key)]
        (when-not (nil? val)
          (let [type (first (.getParameterTypes setter))]
            (.invoke setter instance (into-array [(coerce type val)]))))
        (throw (Exception. (str "property not found for " key)))))))

(def defaults
  {Javac {:includeantruntime false}})

(defn make*
  ([class attrs]
     (let [attrs (merge (defaults class) attrs)]
       (doto (make* class)
         (set-attributes! attrs))))
  ([class]
     (let [signature (into-array Class [Project])]
       (try (.newInstance (.getConstructor class signature)
              (into-array [*ant-project*]))
            (catch NoSuchMethodException e
              (let [instance (.newInstance class)]
                (try (.invoke (.getMethod class "setProject" signature)
                       instance (into-array [*ant-project*]))
                     (catch NoSuchMethodException e))
                instance))))))

(defmacro make [task attrs & forms]
  `(doto (make* ~task ~attrs)
     ~@forms))

(defmacro ant [task attrs & forms]
  `(doto (make* ~task ~attrs)
     ~@forms
     (.setTaskName (if *current-task* (name *current-task*) "null"))
     (.execute)))

(defn get-reference [ref-id]
  (.getReference *ant-project* ref-id))

(defn add-fileset [task attrs]
  (let [attrs (merge {:error-on-missing-dir false} attrs)]
    (.addFileset task (make FileSet attrs))))

(defn add-zipfileset [task attrs]
  (let [attrs (merge {:error-on-missing-dir false} attrs)]
    (.addFileset task (make ZipFileSet attrs))))

(defn fileset-seq [fileset]
  (if (map? fileset)
    (fileset-seq (make FileSet (merge fileset {:error-on-missing-dir false})))
    (map #(.getFile %) (iterator-seq (.iterator fileset)))))

(defn add-manifest [task attrs]
  (let [manifest (Manifest.)]
    (doseq [[key val] attrs :when (seq val)]
      (.addConfiguredAttribute manifest (Manifest$Attribute. key val)))
    (.addConfiguredManifest task manifest)))

(defn path [& paths]
  (let [path (Path. *ant-project*)]
    (doseq [p paths]
      (let [p (if (instance? java.io.File p) (.getPath p) p)]
        (if (.endsWith p "*")
          (add-fileset path {:includes "*.jar" :dir (subs p 0 (dec (count p)))})
          (.. path createPathElement (setPath p)))))
    path))

(defn classpath [& paths]
  (apply path "src" "lib/*" "dev" "lib/dev/*" paths))

(defn args [task & args]
  (doseq [a (remove nil? (flatten args))]
    (.. task createArg (setValue a))))

(defn argline [task args]
  (.. task createArg (setLine args)))

(defn sys [task map]
  (doseq [[key val] map]
    (.addSysproperty task
     (make Environment$Variable {:key (name key) :value val}))))

(defn env [task map]
  (doseq [[key val] map]
    (.addEnv task
     (make Environment$Variable {:key (name key) :value val}))))

(defn init-project []
  (make Project {:basedir *root*}
        (.init)
        (.addBuildListener
         (make NoBannerLogger
               {:message-output-level (if (debug?) Project/MSG_VERBOSE Project/MSG_INFO)
                :output-print-stream  System/out
                :error-print-stream   System/err}))))

(defmacro in-project [& forms]
  `(binding [*ant-project* (init-project)]
     ~@forms))

(defmethod coerce [java.io.File String] [_ str] (file str))
(defmethod coerce :default [type val]
  (if (= String type)
    (str val)
    (if (= EnumeratedAttribute (.getSuperclass type))
      (make type {:value val})
      (try (cast type val)
           (catch ClassCastException e
             val)))))
