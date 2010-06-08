(ns bake
  (:use cake.server))

(defn start-server [port]
  (create-server port eval ".cake/bake.pid"))
