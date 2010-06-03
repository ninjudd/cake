(ns cake.tasks.test
  (:use cake)
  (:import [java.io File]))





(deftask test
  (bake 
   (require 'clojure.test)
   (use 'bake.test)
   (doseq [ns (all-test-namespaces project)]
     (require ns))
   (binding [clojure.test/*report-counters* (ref clojure.test/*initial-report-counters*)]
     (doseq [fn (:fn grouped-tests)]
       (let [test-fn (ns-resolve (symbol (namespace fn)) (symbol (name fn)))
             foo (println "fn:" test-fn)]
         (clojure.test/test-var test-fn)))
(comment
  (println @clojure.test/*report-counters*)

  (doseq [ns (:ns '~to-test)]
    (clojure.test/test-all-vars ns))
               
  (println @clojure.test/*report-counters*)

  (when (:tag to-test)
    (let [tag-map# (map-tags '~test-nses)
          foo#     (println "test-fns" tag-map#)]
      (doseq [tag# (:tag '~to-test)]
        (println "tag:" tag#))))))
             
   (comment let [start# (System/nanoTime)]
            (apply clojure.test/run-tests ('~to-test false))
            (println "Finished in" (/ (- (System/nanoTime) start#) (Math/pow 10 9)) "seconds.\n"))))

       
