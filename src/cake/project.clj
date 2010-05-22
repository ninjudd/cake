(ns cake.project
  (:use cake))

(defn init
  ([] (init "project.clj" "build.clj"))
  ([& files]
     (for [file files :when (.exists (java.io.File file))]
       (load-file file))))