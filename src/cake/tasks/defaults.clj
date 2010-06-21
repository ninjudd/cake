(ns cake.tasks.defaults
  (:use cake)
  (:require [cake.tasks jar test compile dependencies swank clean]))

(deftask default #{help})