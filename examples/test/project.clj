(defproject test-example "0.1.0-SNAPSHOT"
  :description "cake example project"
  :tasks [foo :exclude [uberjar jar]]
  :dependencies [[clojure "1.2.0-master-SNAPSHOT"]
                 [clojure-contrib "1.2.0-SNAPSHOT"]
                 [clojure-useful "0.2.2" :exclusions [clojure]]
                 [swank-clojure "1.2.1"]
                 [tokyocabinet "1.23-SNAPSHOT"]]
  :dev-dependencies [[clojure-complete "0.1.0"]
                     [autodoc "0.7.1"]])

(deftask bar
  (bake (:use useful)
        [foo (prompt-read "enter foo")
         bar (prompt-read "enter bar")
         pw  (prompt-read "enter password" :echo false)]
        (println "project:" *project*)
        (println "opts:" *opts*)
        (println "foo:" foo)
        (println "bar:" bar)
        (println "password is" (count pw) "characters long")
        (println "baz!")
        (Thread/sleep 2000)
        (println "done sleeping!")
        (verify true "true is false!")))
