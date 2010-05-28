(deftask compile
  (println (seq (.getURLs (java.lang.ClassLoader/getSystemClassLoader))))
  (println "compiling...")
  (println (ns-name *ns*))
  (let [root       (:root project)
        classpath  (for [dir ["/src" "/lib*"]] (str root dir))]
    (println classpath)
    (cake.ant/ant org.apache.tools.ant.taskdefs.Javac {:destdir (java.io.File. root "classes")
                :fork true
                :classpath (cake.ant/ant org.apache.tools.ant.types.Path {:path (apply str (interpose ":" classpath))})
                :srcdir (cake.ant/ant org.apache.tools.ant.types.Path {:path (java.io.File. root "src")})
                :includes "*.clj"})))

