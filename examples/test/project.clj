(defproject test-example "0.1.0-SNAPSHOT"
  :description "cake example project"
  :dependencies [[clojure "1.2.0"]
                 [clojure-contrib "1.2.0"]
                 [tokyocabinet "1.23-SNAPSHOT"]]
  :dev-dependencies [[autodoc "0.7.1"]]
  :context development
  :aot [speak servlet]
  :main speak)

(defcontext qa
  :foo 1
  :bar 2)

(defcontext development
  :baz 1
  :bar 8)

(deftask bar
  (bake [out System/out
         foo "foo"
         bar {1 2 3 4}]
        (prn out)
        (prn foo)
        (prn bar)))
