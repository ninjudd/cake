(ns bake
  (:use cake.server)
  (:require clojure.main))

(defn eval-multi [form]
  (clojure.main/with-bindings
    (if (vector? form)
      (doseq [f form] (eval f))
      (eval form))))

(defn start-server [port]
  (create-server port eval-multi))
