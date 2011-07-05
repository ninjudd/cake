(ns cake.tasks.coverage
  (:use cake [cake.core :rename {bake core-bake}]
        [cake.file :only [file]]
        [useful.debug :only [?]]
        [clojure.tools.namespace :only [find-namespaces-in-dir
                                        find-clojure-sources-in-dir]]
        [clojure.java.io :only [reader]]))

(def fake-bake false)
(defmacro bake [& args]
  (if fake-bake
    (last args)
    `(core-bake ~@args)))

(defn testable-namespaces []
  (mapcat (fn [dir]
            (find-namespaces-in-dir (java.io.File. dir)))
          (:source-path *project*)))

(defn testable-symbols [namespaces]
  (bake (:use [useful.debug :only [?]])
    [namespaces namespaces]
    (for [n namespaces
          [sym var] (ns-publics (find-ns (doto n require)))
          :when (and (bound? var) (fn? @var) (not (-> var meta :dont-test)))]
      (apply symbol (map str [n sym])))))

(defn clj-test-files []
  (apply concat (map (comp find-clojure-sources-in-dir file)
                     (:test-path *project*))))

(let [end (Object.)]
  (defn read-all [reader]
    (try (doall
          (take-while (complement #{end})
                      (repeatedly #(read reader false end))))
         (catch Throwable _))))

(defn used-symbols [file]
  (->> (read-all (java.io.PushbackReader. (reader file)))
       flatten
       (filter symbol?)
       (map (comp symbol name)))) ;; strip namespace - too hard to do right

(defn un-tested-symbols [testables]
  (let [tested (set (mapcat used-symbols (clj-test-files)))
        untested (remove (comp tested symbol name) testables)]
    (into {} (for [[ns syms] (group-by namespace untested)]
               [ns (map name syms)]))))

(deftask coverage
  (let [testables (testable-symbols (testable-namespaces))
        needed-additions (not-empty (un-tested-symbols testables))
        untested-count (count (mapcat val needed-additions))
        tested-ratio (- 1 (/ untested-count (count testables)))
        [encouragement invert?] (cond
                       (= 1 tested-ratio)    ["Great!"]
                       (<= 1/2 tested-ratio) ["Almost done..." true]
                       (zero? tested-ratio)  ["Get to work!"]
                       :else                 ["Keep it up!"])
        [fmt ratio] (if invert?
                      ["still need tests." (- 1 tested-ratio)]
                      ["are tested!" tested-ratio])
        fmt (str "%n%n%2.0f%% of public functions " fmt " " encouragement)]
    (when needed-additions
      (print "The following functions appear to be untested:")
      (let [width (apply max (map #(.length (key %)) needed-additions))
            fmt (str "%n  %" width "s:")]
        (doseq [[ns syms] needed-additions]
          (print (format fmt ns))
          (doseq [s syms] (print (str " " s))))))
    (println (format fmt (* 100.0 ratio)))))
