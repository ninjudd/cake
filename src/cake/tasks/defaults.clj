(ns cake.tasks.defaults
  (:use cake)
  (:require [cake.tasks help jar test compile dependencies swank]))

(deftask default => help)