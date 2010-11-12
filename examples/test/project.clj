(defproject test-example "0.1.0-SNAPSHOT"
  :description "cake example project"
  :tasks [foo :exclude [uberjar jar]]
  :dependencies [[clojure "1.2.0"]
                 [clojure-contrib "1.2.0"]
                 [tokyocabinet "1.23-SNAPSHOT"]]
  :dev-dependencies [[clojure-complete "0.1.0"]
                     [autodoc "0.7.1"]]
  :context development
  :aot [bar])

(defcontext qa
  :foo 1
  :bar 2)

(defcontext development
  :baz 1
  :bar 8)