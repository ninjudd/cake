(ns bake.core
  (:use cake
        [clojure.string :only [join]])
  (:require [clojure.stacktrace :as stacktrace]))

(defn print-stacktrace [e]
  (stacktrace/print-cause-trace e)
  (flush))

(defn merge-in
  "Merge two nested maps."
  [left right]
  (if (map? left)
    (merge-with merge-in left right)
    right))

(defn log [& message]
  (println (format "%11s %s" (str "[" *current-task* "]") (join " " message))))

(defn in-cake-jvm?
  "Returns true if we are running from a jvm started by cake."
  []
  (not (nil? (System/getProperty "cake.project"))))

(defn in-project-classloader?
  "Returns true if this code is running in the project classloader."
  []
  (= java.net.URLClassLoader
     (class (.getClassLoader clojure.lang.RT))))

(defn debug? []
  (boolean (or (:d *opts*) (:debug *opts*))))

(defn force? []
  (boolean (or (:F *opts*) (:force *opts*))))

(defn verbose? []
  (boolean (or (:v *opts*) (:verbose *opts*))))

(defn current-context []
  (if-let [context (get-in *opts* [:context 0])]
    (symbol context)))

(defn project-with-context [context]
  (if (nil? context)
    *project-root*
    (merge-in *project-root*
              (assoc (context *context*)
                :context context))))

(defmacro with-context [context & forms]
  `(let [context# (symbol (name (or ~context (:context *project*))))]
     (binding [*project* (project-with-context context#)]
       ~@forms)))

(defn set-project! [project]
  (alter-var-root #'*project* (fn [_] project)))

(defn set-context! [context]
  (let [context (or context (:context *project*))]
    (set-project! (project-with-context context))))

(defmacro with-context! [context & forms]
  `(try (set-context! ~context)
        (do ~@forms)
        (set-project! *project-root*)))

(defn context? [context]
  "Returns true if the argument is the currently selected context. Argument can
   be anything that clojure.core/name turns into a string."
  (= (name (:context *project*)) (name context)))

(defn os-name []
  (let [name (System/getProperty "os.name")]
    (condp #(.startsWith %2 %1) name
      "Linux"    "linux"
      "Mac OS X" "macosx"
      "SunOS"    "solaris"
      "Windows"  "windows"
      "unknown")))

(defn os-arch []
  (or (first (:arch *opts*))
      (get *config* "project.arch")
      (let [arch (System/getProperty "os.arch")]
        (case arch
          "amd64" "x86_64"
          "i386"  "x86"
          arch))))