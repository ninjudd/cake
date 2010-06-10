(ns bake
  (:use cake.server bake.utils)
  (:require clojure.main bake.swank))

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
  (create-server port eval-multi :quit verify-quit :reload reload-files)
  (bake.swank/start)
  nil)
