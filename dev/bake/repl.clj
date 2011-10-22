(ns bake.repl
  (:use [cake :only [*project*]]))

(defn- reset-in []
  (while (.ready *in*) (.read *in*)))

(defn repl [marker]
  (((or (eval (:repl-wrapper *project*)) identity)
    (fn []
      (clojure.main/repl
       :init   #(in-ns 'user)
       :caught #(do (reset-in) (clojure.main/repl-caught %))
       :prompt #(println (str marker (ns-name *ns*))))))))
