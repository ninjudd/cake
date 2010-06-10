(ns bake
  (:use cake.server)
  (:require clojure.main bake.swank))

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

(defn verify-quit [ok]
  (when (= 0 (bake.swank/num-connections))
    ok))

(defn reload-files [_]
  (doseq [file (read)] (load-file file)))

(defn start-server [port]
  (create-server port eval-multi :quit verify-quit :reload reload-files))
