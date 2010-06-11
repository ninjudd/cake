(ns bake
  (:use cake.server
        [cake.contrib.find-namespaces :only [read-file-ns-decl]])
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
  (doseq [file (read)]
    (if-let [ns (second (read-file-ns-decl (java.io.File. file)))]
      (when (find-ns ns) ;; don't reload namespaces that aren't already loaded
        (load-file file)))))

(defn start-server [port]
  (create-server port eval-multi :quit verify-quit :reload reload-files)
  (bake.swank/start)
  nil)
