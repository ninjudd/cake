(ns bake
  (:require clojure.main bake.swank
            [cake.server :as server]))

(defn eval-multi [form]
  (clojure.main/with-bindings
    (if (vector? form)
      (doseq [f form] (eval f))
      (eval form))))

(defn quit []
  (when (= 0 (bake.swank/num-connections))
    (server/quit)))

(defn start-server [port]
  (server/create port eval-multi :quit quit)
  (bake.swank/start)
  nil)
