(ns cake.ant)

(defn- setter [[key value]]
  (let [method (symbol
                (apply str ".set"
                  (for [w (.split (name key) "-")]
                    (str (.toUpperCase (.substring w 0 1))
                         (.toLowerCase (.substring w 1))))))]
    (list method value)))

(defmacro make [class attrs]
  `(doto (new ~class)
     ~@(map setter attrs)))

(def ant-project (atom nil))

(defmacro ant [task attrs & forms]
  `(doto (make ~task ~attrs)
     (.setProject @ant-project)
     ~@forms
     (.execute)))

(defn init-project [root]
  (compare-and-set! ant-project nil
    (doto (org.apache.tools.ant.Project.)
      (.init)
      (.setBasedir root)
      (.addBuildListener (doto (org.apache.tools.ant.NoBannerLogger.)
                           (.setMessageOutputLevel org.apache.tools.ant.Project/MSG_INFO)
                           (.setOutputPrintStream System/out)
                           (.setErrorPrintStream System/err))))))