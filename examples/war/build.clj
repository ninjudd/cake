(use 'cake.tasks.compile 'cake.tasks.test)

(deftask compile => test)

(deftask sayhi => compile
  (println "hi"))

