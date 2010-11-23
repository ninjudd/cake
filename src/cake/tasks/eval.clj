(ns cake.tasks.eval
  (:use cake cake.core
        [cake.file :only [file]]))


(defn- read-form [string]
  (let [form (if (= "-" string)
               (read)
               (read-string string))]
    (if (:q *opts*)
      form
      (list 'prn form))))

(deftask eval
  "Eval the given forms in the project JVM."
  "Read a form from stdin for each - provided."
  {forms :eval}
  (bake [forms (map read-form forms)]
        (eval `(do ~@forms))))

(deftask filter
  "Thread each line in stdin through the given forms, printing the results."
  "The line is passed as a string with a trailing newline, and println is called with the result of the final form."
  {forms :filter}
  (bake (:use [clojure.java.io :only [reader]])
        [forms (map read-string forms)]
        (doseq [line (line-seq (reader *in*))]
          (spit "/tmp/foo" (prn-str `(-> ~line ~@forms println)))
          (eval `(-> ~line ~@forms println)))))

(deftask run
  "Execute a script in the project jvm."
  {[script] :run}
  (bake [script (.getPath (file script))]
        (load-file script)))