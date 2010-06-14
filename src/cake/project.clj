(ns cake.project
  (:require cake)
  (:import [java.io File]))

(defn init []
  (doseq [file ["project.clj" "build.clj"] :when (.exists (java.io.File. file))]
    (load-file file))
  (when-not @cake/cake-project (require 'cake.tasks.new))
  (require 'cake.tasks.help))

