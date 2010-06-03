(ns cake.oven
  (:use cake.ant)
  (:import [org.apache.tools.ant.taskdefs Java]))

(defn bake [form]
  "execute a form in a fork of the jvm with the classpath of your project"
  (ant Java {:classname   "clojure.main"
             :classpath   (path "src" "lib/*" "test")
             :fork        true
             :failonerror true}
    (args ["-e" (prn-str form)])))
