(ns cake.tasks.repl
  (:use cake cake.ant)
  (:import [org.apache.tools.ant.taskdefs Java]))

(defn repl [project]
  (ant Java {:classname   "jline.ConsoleRunner"
             :classpath   (classpath project)
             :fork        true}
       (args ["clojure.main"])))

(deftask repl
  (comment bake (clojure.main/repl))
  (repl project))
