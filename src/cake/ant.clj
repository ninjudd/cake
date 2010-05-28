(ns cake.ant
  (:use [clojure.useful :only [conj-vec]])
  (:import [org.apache.tools.ant Project NoBannerLogger]
           [org.apache.tools.ant.types Path]))

(defn- setter [key]
  (symbol
    (apply str "set"
      (for [w (.split (name key) "-")]
        (str (.toUpperCase (.substring w 0 1))
             (.toLowerCase (.substring w 1)))))))

(defn set-attribute! [instance [key value]]
  (when-not (nil? value)
    (let [method (eval `(memfn ~(setter key) v#))]
      (method instance value))
    instance))

(defn set-attributes! [instance attrs]
  (reduce set-attribute! instance attrs))

(defmacro make [class attrs & forms]
  `(doto (new ~class)
     (set-attributes! ~attrs)
     ~@forms))

(def ant-project (atom nil))

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
  (Path. @ant-project (apply str (interpose ":" paths))))

(defn init-project [root]
  (compare-and-set! ant-project nil
    (make Project {:basedir root}
      (.init)
      (.addBuildListener
       (make NoBannerLogger
         {:message-output-level Project/MSG_INFO
          :output-print-stream  System/out
          :error-print-stream   System/err})))))