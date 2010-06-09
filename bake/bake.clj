(ns bake
  (:use cake.server)
  (:require clojure.main))

; From clojure.useful, but I don't want to implicitly include it in every project.
(defmacro defm [name & fdecl]
  "Define a function with memoization. Takes the same arguments as defn."
  `(let [var (defn ~name ~@fdecl)]
     (alter-var-root var #(with-meta (memoize %) (meta %)))
     var))

(defn eval-multi [form]
  (clojure.main/with-bindings
    (if (vector? form)
      (doseq [f form] (eval f))
      (eval form))))

(defn start-server [port]
  (create-server port eval-multi))
