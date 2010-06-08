(ns cake.ant
  "Lancet-inspired ant helpers."
  (:import [org.apache.tools.ant Project NoBannerLogger]
           [org.apache.tools.ant.types Path FileSet ZipFileSet EnumeratedAttribute Environment$Variable]
           [org.apache.tools.ant.taskdefs Echo Manifest Manifest$Attribute]
           [java.beans Introspector]))

(def ant-project nil)

(defmulti coerce (fn [type val] [type (class val)]))

(defn- property-key [property]
  (keyword (.. (re-matcher #"\B([A-Z])" (.getName property))
               (replaceAll "-$1")
               toLowerCase)))

(defn property-setters [class]
  (reduce
   (fn [map property]
     (assoc map (property-key property) (.getWriteMethod property)))
   {} (.getPropertyDescriptors (Introspector/getBeanInfo class)))  )

(defn set-attributes! [instance attrs]
  (let [setters (property-setters (class instance))]
    (doseq [[key val] attrs]
      (if-let [setter (setters key)]
        (when-not (nil? val)
          (let [type (first (.getParameterTypes setter))]
            (.invoke setter instance (into-array [(coerce type val)]))))
        (throw (Exception. (str "property not found for " key)))))))

(defn make*
  ([class attrs]
     (doto (make* class)
       (set-attributes! attrs)))
  ([class]
     (let [signature (into-array Class [Project])]
       (try (.newInstance (.getConstructor class signature)
              (into-array [ant-project]))
            (catch NoSuchMethodException e
              (let [instance (.newInstance class)]
                (try (.invoke (.getMethod class "setProject" signature)
                       instance (into-array [ant-project]))
                     (catch NoSuchMethodException e))
                instance))))))

(defmacro make [task attrs & forms]
  `(doto (make* ~task ~attrs)
     ~@forms))

(def current-task nil)

(defmacro ant [task attrs & forms]
  `(doto (make* ~task ~attrs)
     ~@forms
     (.setTaskName (name current-task))
     (.execute)))

(defn get-reference [ref-id]
  (.getReference ant-project ref-id))

(defmacro add-fileset [task attrs & forms]
  (let [attrs (merge {:error-on-missing-dir false} attrs)]
    `(.addFileset ~task
       (make FileSet ~attrs ~@forms))))

(defmacro add-zipfileset [task attrs & forms]
  (let [attrs (merge {:error-on-missing-dir false} attrs)]
    `(.addFileset ~task
       (make ZipFileSet ~attrs ~@forms))))

(defn add-manifest [task attrs]
  (let [manifest (Manifest.)]
    (doseq [[key val] attrs :when (seq val)]
      (.addConfiguredAttribute manifest (Manifest$Attribute. key val)))
    (.addConfiguredManifest task manifest)))

(defn path [& paths]
  (let [path (Path. ant-project)]
    (doseq [p paths]
      (if (.endsWith p "*")
        (add-fileset path {:includes "*.jar" :dir (subs p 0 (dec (count p)))})
        (.. path createPathElement (setPath p))))
    path))

(defn classpath [project & paths]
  (apply path (:source-path project) (str (:library-path project) "/*") paths))

(defn args [task args]
  (doseq [a args]
    (.. task createArg (setValue a))))

(defn sys [task map]
  (doseq [[key val] map]
    (.addSysproperty task
     (make Environment$Variable {:key (name key) :value val}))))

(defn env [task map]
  (doseq [[key val] map]
    (.addEnv task
     (make Environment$Variable {:key (name key) :value val}))))

(defn init-project [project outs]
  (let [outs (java.io.PrintStream. outs)]
    (make Project {:basedir (:root project)}
          (.init)
          (.addBuildListener
           (make NoBannerLogger
                 {:message-output-level Project/MSG_INFO
                  :output-print-stream  outs
                  :error-print-stream   outs})))))

(defn log [& message]
  (ant Echo {:message (apply str message)}))

(defmethod coerce [java.io.File String] [_ str] (java.io.File. str))
(defmethod coerce :default [type val]
  (if (= String type)
    (str val)
    (if (= EnumeratedAttribute (.getSuperclass type))
      (make type {:value val})
      (try (cast type val)
           (catch ClassCastException e
             val)))))
