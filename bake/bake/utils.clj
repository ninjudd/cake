(ns bake.utils)
;; Functions from clojure.useful, but I don't want to implicitly include it in every project.

(defmacro defm [name & fdecl]
  "Define a function with memoization. Takes the same arguments as defn."
  `(let [var (defn ~name ~@fdecl)]
     (alter-var-root var #(with-meta (memoize %) (meta %)))
     var))

(defmacro if-ns [ns-reference then-form else-form]
  "Try to load a namespace reference. If sucessful, evaluate then-form otherwise evaluate else-form."
  `(try (ns ~(.getName *ns*) ~ns-reference)
        (eval '~then-form)
        (catch java.io.FileNotFoundException e#
          (eval '~else-form))))