(ns cake.tasks.eval
  (:use cake
        [cake.core :only [deftask bake]]
        [cake.file :only [with-root file]]
        [cake.utils :only [*readline-marker* keepalive!]]
        [bake.repl :only [repl]]))

(defn- read-form [string]
  (let [form (if (= "-" string)
               (read)
               (read-string string))]
    (if (:q *opts*)
      form
      (list 'prn form))))

(deftask eval #{compile-java}
  "Eval the given forms in the project JVM."
  "Read a form from stdin for each - provided."
  {forms :eval cake? :cake}
  (if cake?
    (eval `(do ~@(map read-form forms)))
    (bake [forms (map read-form forms)]
      (eval `(do ~@forms)))))

(deftask filter #{compile-java}
  "Thread each line in stdin through the given forms, printing the results."
  "The line is passed as a string with a trailing newline, and println is called with the result of the final form."
  {forms :filter}
  (bake (:use [clojure.java.io :only [reader]])
        [forms (map read-string forms)]
        (doseq [line (line-seq (reader *in*))]
          (spit "/tmp/foo" (prn-str `(-> ~line ~@forms println)))
          (eval `(-> ~line ~@forms println)))))

(deftask run #{compile-java}
  "Execute a script in the project jvm."
  "Pass a path to a file and cake will run that file in the persistent JVM.
  If you pass the -m option, cake will look for :main in your project and will
  try to run it in the persistent JVM."
  {[script] :run m :m}
  (cond 
   m (bake [main (:main *project*)
            args (remove #{"run" "-m"} *args*)]
           (if main
             (do (require main) 
                 (-> (str main "/-main") symbol resolve (apply args)))
             (println ":main is not specified in your project.")))
   script (bake [script (with-root *pwd* (file script))
                 args   (rest (drop-while (partial not= (str script)) *args*))]
                (if (.exists script) 
                  (binding [*command-line-args* args]
                    (load-file (str script)))
                  (println "File does not exist.")))
   :else (println "You need to either pass me a file, or pass the -m option. " 
                  "Run `cake help run` for more information.")))

(deftask repl #{compile-java}
  "Start an interactive shell with history and tab completion."
  {cake? :cake}
  (keepalive!)
  (if cake?
    (repl *readline-marker*)
    (bake (:use bake.repl) [marker *readline-marker*]
          (repl marker))))
