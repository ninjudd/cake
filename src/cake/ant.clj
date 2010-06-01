(ns cake.ant
  "Lancet-inspired ant helpers."
  (:use [clojure.useful :only [conj-vec]])
  (:import [org.apache.tools.ant Project NoBannerLogger]
           [org.apache.tools.ant.types Path FileSet]
           [java.beans Introspector]))

(def ant-project (atom nil))

(defmulti  coerce (fn [type val] [type (class val)]))
(defmethod coerce [java.io.File String] [_ str] (java.io.File. str))
(defmethod coerce :default [type val]
  (if (= String type)
    (str val)
    (try (cast type val)
         (catch ClassCastException e
           val))))

(defn- property-key [property]
  (keyword (.. (re-matcher #"\B([A-Z])" (.getName property))
               (replaceAll "-$1")
               toLowerCase)))

(defn set-attributes! [instance attrs]
  (doseq [property (.getPropertyDescriptors (Introspector/getBeanInfo (class instance)))]
    (let [key    (property-key property)
          val    (attrs key)
          setter (.getWriteMethod property)]
      (when-not (or (nil? val) (nil? setter))
        (let [type (first (.getParameterTypes setter))]
          (.invoke setter instance (into-array [(coerce type val)])))))))

(defn make*
  ([class attrs]
     (doto (make* class)
       (set-attributes! attrs)))
  ([class]
     (let [signature (into-array Class [Project])]
       (try (.newInstance (.getConstructor class signature)
              (into-array [@ant-project]))
            (catch NoSuchMethodException e
              (let [instance (.newInstance class)]
                (try (.invoke (.getMethod class "setProject" signature)
                       instance (into-array [@ant-project]))
                     (catch NoSuchMethodException e))
                instance))))))

(defmacro make [task attrs & forms]
  `(doto (make* ~task ~attrs)
     ~@forms))

(defmacro ant [task attrs & forms]
  `(doto (make* ~task ~attrs)
     ~@forms
     (.execute)))

(defmacro add-fileset [task type attrs & forms]
  `(.addFileset ~task
     (make ~type ~attrs ~@forms)))

(defn get-reference [ref-id]
  (.getReference @ant-project ref-id))


(defmacro ant [task attrs & forms]
  (let [forms (if (= false (first forms))
                (rest forms)
                (conj-vec forms '(.execute)))]
    `(doto (new ~task)
       (.setProject @ant-project)
       (set-attributes! ~attrs)
       ~@forms)))

(defn ant-path [& paths]
  (let [project-path (Path. @ant-project)]
    (doseq [p paths]
      (if (.endsWith p "*")
        (.addFileset project-path
                     (doto (new FileSet)
                       (.setDir (java.io.File. (.getBaseDir @ant-project) (.substring p 0 (dec (.length p)))))
                       (.setIncludes "*.jar")))
        (.add project-path (Path. @ant-project p))))
    project-path))

(defn args [task args]
  (doseq [a args]
    (.. task createArg (setValue a))))

(defn init-project [root]
  (compare-and-set! ant-project nil
    (make Project {:basedir root}
      (.init)
      (.addBuildListener
       (make NoBannerLogger
         {:message-output-level Project/MSG_INFO
          :output-print-stream  System/out
          :error-print-stream   System/err})))))