(ns cake.tasks.test
  (:use cake
        [clojure.contrib.find-namespaces :only [find-namespaces-in-dir]])
  (:import [java.io File]))

(defn prep-opt [str]
  (if (.startsWith str ":")
    (read-string str)
    (symbol str)))

(defn group-opts [coll]
  (group-by #(cond (and (namespace %) (name %)) :fn
                   (keyword? %)                 :tag
                   :else                        :ns)
            coll))

(deftask test
  (println "opts:" opts)
  (let [test-nses (find-namespaces-in-dir (File. (:root project) "test"))
        to-test   (:test opts)
        to-test   (if (nil? to-test)
                    test-nses
                    (map prep-opt to-test))
        to-test   (group-opts to-test)
        foo       (println "b:" to-test)]
    (bake
     (require 'clojure.test)
     (doseq [ns# '~test-nses]
       (require ns#))
     (binding [clojure.test/*report-counters* (ref clojure.test/*initial-report-counters*)]
       (doseq [fn# (:fn '~to-test)]
         (let [test-fn# (ns-resolve (symbol (namespace fn#)) (symbol (name fn#)))]
           (clojure.test/test-var test-fn#)))

       (println @clojure.test/*report-counters*)

       (doseq [ns# (:ns '~to-test)]
         (clojure.test/test-all-vars ns#))

       (println @clojure.test/*report-counters*)

       (comment doseq [tag# (:tag '~to-test)]
                (println "tag:" tag#)

                ))

     (comment let [start# (System/nanoTime)]
              (apply clojure.test/run-tests ('~to-test false))
              (println "Finished in" (/ (- (System/nanoTime) start#) (Math/pow 10 9)) "seconds.\n")))))
