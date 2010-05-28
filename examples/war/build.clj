(deftask compile
  (println (seq (.getURLs (java.lang.ClassLoader/getSystemClassLoader))))
  (println "compiling...")
  (println (ns-name *ns*))
  (let [root       (:root project)
        classpath  (for [dir ["/src" "/lib*"]] (str root dir))
        cp-fileset (ant FileSet)]
    (println classpath)
    (ant Javac {:destdir (java.io.File. root "classes")
                :fork true
                :classpath (ant Path {:path (apply str (interpose ":" classpath))})
                :srcdir (ant Path {:path (java.io.File. root "src")})
                :includes "*.clj"})))

