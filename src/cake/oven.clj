(ns cake.oven
  (:use cake.ant
        [cake :only [project]])
  (:import [org.apache.tools.ant.taskdefs Java]))

(defmacro bake [& body]
  "execute a body in a fork of the jvm with the classpath of your project"
  `(ant Java {:classname   "clojure.main"
              :classpath   (classpath project (:test-path project) (System/getProperty "bakepath"))
              :fork        true
              :failonerror true}
        (args ["-e" (prn-str '(do ~@body))])))
