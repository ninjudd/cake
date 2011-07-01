(ns cake.tasks.classpath
  (:use [cake.core :only [deftask]]
        [cake.project :only [*classloader*]]
        [classlojure :only [get-classpath base-classloader]]))

(deftask classpath
  "Print the classpath for the cake and project classloaders."
  (println "Cake classpath:")
  (doseq [path (get-classpath base-classloader)]
    (println " " path))
  (println "Project classpath:")
  (doseq [path (get-classpath *classloader*)]
    (println " " path)))
