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

(deftask eval-file #{compile-java}
  "Eval the given file in the project JVM with *command-line-args* bound."
  {[script] :eval-file cake? :cake}
  (let [script (with-root *pwd* (file script))
        args   (drop 2 *args*)]
    (if-not (.exists script)
      (println "file does not exist:" script)
      (if cake?
        (binding [*command-line-args* args]
          (load-file (str script)))
        (bake [script (str script)
               args   args]
          (binding [*command-line-args* args]
            (load-file script)))))))

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
  "Execute your project's main function in the persistent JVM."
  (bake [main (:main *project*)
         args (rest *args*)]
    (if main
      (do (require main)
          (-> (str main "/-main") symbol resolve (apply args)))
      (println ":main is not specified in your project."))))

(deftask repl #{compile-java}
  "Start an interactive shell with history and tab completion."
  {cake? :cake [ns] :repl}
  (keepalive!)
  (let [ns (if ns
             (symbol ns)
             (symbol (str "repl-" (swap! repl-count inc))))]
    (if cake?
      (repl *readline-marker* ns)
      (bake (:use bake.repl) [marker *readline-marker*, ns ns]
        (repl marker ns)))))
