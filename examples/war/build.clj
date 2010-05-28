(deftask compile
  ;; (declare *compile-path*)
  (println (seq (.getURLs (java.lang.ClassLoader/getSystemClassLoader))))
  (println "compiling...")
  (binding [*compile-path* "classes"]
    (compile 'servlet)))

