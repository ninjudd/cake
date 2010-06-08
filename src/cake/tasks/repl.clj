(ns cake.tasks.repl
  (:use cake cake.ant)
  (:require [clojure.contrib.server-socket :as ss]))

(deftask repl
  (ss/create-repl-server 9229))

(comment 
  (require '[clojure.contrib.server-socket :as ss])
  (import '(java.net InetAddress))
  (ss/create-repl-server 9225 0 (InetAddress/getByName "127.0.0.1")))