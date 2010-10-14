(ns cake.tasks.pallet
  "A cake task for controlling pallet.

   Adapted from Hugo Duncan's pallet-lein at http://github.com/hugoduncan/pallet-lein."
  (:use cake cake.core))

(deftask pallet
  "Launch pallet tasks from the command line.

   For a list of tasks
     lein pallet help"
  (bake (:require [clojure.string :as str])
	[options (:pallet *opts*)]
	(do
	  (try
	    (require 'pallet.main)
	    (catch java.io.FileNotFoundException e
	      (println "Error loading pallet: " (.getMessage e))
	      (println "You need to have pallet as a project dependency")
	      (println "or installed in ~/.cake/project.clj")))
	  (if-let [m (ns-resolve 'pallet.main (symbol "-main"))]
	    (apply m options)
	    (do
	      (println "failed to resolve " 'pallet.main (symbol "-main")))))))