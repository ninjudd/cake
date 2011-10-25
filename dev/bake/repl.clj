(ns bake.repl
  (:use [cake :only [*project* repl-count]]))

(defn- reset-in []
  (while (.ready *in*) (.read *in*)))

(defmacro with-wrapper [& forms]
  `(((or (eval (:repl-wrapper *project*)) identity)
     (fn []
       (let [init# (eval (:repl-init *project*))]
         (when (fn? init#)
           (init#)))
       ~@forms))))

(defn repl [marker ns]
  (with-wrapper
    (clojure.main/repl
     :init   #(do (in-ns ns)
                  (refer 'clojure.core))
     :caught #(do (reset-in) (clojure.main/repl-caught %))
     :prompt #(println (str marker (ns-name *ns*))))))
