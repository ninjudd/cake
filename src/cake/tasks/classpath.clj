(ns cake.tasks.classpath
  (:use [cake :only [*classloader*]]
        [cake.core :only [deftask]]
        [classlojure :only [get-classpath base-classloader]]))

(deftask classpath
  "Print the classpath for the cake and project classloaders."
  (println "\ncake classpath:")
  (doseq [path (get-classpath base-classloader)]
    (println " " path))
  (println "\nproject classpath:")
  (doseq [path (get-classpath *classloader*)]
    (println " " path))
  (println))
