(ns cake.tasks.defaults
  (:use cake)
  (:require [cake.tasks help jar test compile dependencies swank clean]))

(deftask default => help)