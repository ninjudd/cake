<<<<<<< Updated upstream:src/cake/tasks/classpath.clj
(ns cake.tasks.classpath
  (:use [cake :only [*classloader*]]
        [cake.core :only [deftask]]
=======
(ns cake.tasks.misc
  (:use [cake :only [history]]
        [cake.core :only [deftask]]
        [cake.project :only [*classloader*]]
>>>>>>> Stashed changes:src/cake/tasks/misc.clj
        [classlojure :only [get-classpath base-classloader]]))

(deftask classpath
  "Print the classpath for the cake and project classloaders."
  (println "\ncake classpath:")
  (doseq [path (get-classpath base-classloader)]
    (println " " path))
  (println "\nproject classpath:")
  (doseq [path (get-classpath *classloader*)]
<<<<<<< Updated upstream:src/cake/tasks/classpath.clj
    (println " " path))
  (println))
=======
    (println " " path)))

(deftask history
  "Print a list of the most recent cake commands executed on this JVM."
  (doseq [args @history]
    (apply println "cake" args)))
>>>>>>> Stashed changes:src/cake/tasks/misc.clj
