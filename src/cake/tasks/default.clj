(ns cake.tasks.default
  (:use cake.core)
  (:require [cake.tasks help jar test compile deps release swank file version eval bake]))

(deftask default #{help})