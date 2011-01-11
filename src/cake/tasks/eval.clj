(ns cake.tasks.eval
  (:use cake
        [cake.core :only [deftask bake]]
        [cake.file :only [file]]
        [bake.repl :only [repl]]))

(defn- read-form [string]
  (let [form (if (= "-" string)
               (read)
               (read-string string))]
    (if (:q *opts*)
      form
      (list 'prn form))))

(deftask eval #{compile}
  "Eval the given forms in the project JVM."
  "Read a form from stdin for each - provided."
  {forms :eval}
  (bake [forms (map read-form forms)]
        (eval `(do ~@forms))))

(deftask filter #{compile}
  "Thread each line in stdin through the given forms, printing the results."
  "The line is passed as a string with a trailing newline, and println is called with the result of the final form."
  {forms :filter}
  (bake (:use [clojure.java.io :only [reader]])
        [forms (map read-string forms)]
        (doseq [line (line-seq (reader *in*))]
          (spit "/tmp/foo" (prn-str `(-> ~line ~@forms println)))
          (eval `(-> ~line ~@forms println)))))

(deftask run #{compile}
  "Execute a script in the project jvm."
  {[script] :run}
  (bake [script (str
                 (let [fscript (java.io.File. script)]
                   (if (.isAbsolute fscript)
                     fscript
                     (file *pwd* script))))]
        (binding [*command-line-args* (-> *opts* :run rest)]
          (load-file script))))

(deftask repl #{compile}
  "Start an interactive shell with history and tab completion."
  {cake? :cake}
  (if cake?
    (repl (read))
    (bake (:use bake.repl) [marker (read)]
          (repl marker))))
