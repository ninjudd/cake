(ns bake.repl
  (:use [cake :only [*project*]]))

(defn- reset-in []
  (while (.ready *in*) (.read *in*)))

(defmacro with-wrapper [& forms]
  `(((or (eval (:repl-wrapper *project*)) identity)
     (fn []
       (let [init# (eval (:repl-init *project*))]
         (when (fn? init#)
           (init#)))
       ~@forms))))

(defn repl [marker]
  (with-wrapper
    (clojure.main/repl
     :init   #(in-ns 'user)
     :caught #(do (reset-in) (clojure.main/repl-caught %))
     :prompt #(println (str marker (ns-name *ns*))))))
