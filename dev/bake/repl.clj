(ns bake.repl)

(defn- reset-in []
  (while (.ready *in*) (.read *in*)))

(defn repl [marker]
  (clojure.main/repl
   :init   #(in-ns 'user)
   :caught #(do (reset-in) (clojure.main/repl-caught %))
   :prompt #(println (str marker (ns-name *ns*)))))
