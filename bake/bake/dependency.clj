;; by Stuart Sierra, http://stuartsierra.com/
;; December 1, 2010

;; Copyright (c) Stuart Sierra, 2010. All rights reserved.  The use
;; and distribution terms for this software are covered by the Eclipse
;; Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this
;; distribution.  By using this software in any fashion, you are
;; agreeing to be bound by the terms of this license.  You must not
;; remove this notice, or any other, from this software.

;; copied from lazytest.dependency

(ns bake.dependency
  "Bidirectional graphs of dependencies and dependent objects."
  (:use [clojure.set :only (union)]))

(defn graph "Returns a new, empty, dependency graph." []
  {:dependencies {}
   :dependents {}})

(defn- transitive
  "Recursively expands the set of dependency relationships starting at (get m x)"
  [m x]
  (reduce (fn [s k]
            (union s (transitive m k)))
          (get m x) (get m x)))

(defn dependencies
  "Returns the set of all things x depends on, directly or transitively."
  [graph x]
  (transitive (:dependencies graph) x))

(defn dependents
  "Returns the set of all things which depend upon x, directly or transitively."
  [graph x]
  (transitive (:dependents graph) x))

(defn depends?
  "True if x is directly or transitively dependent on y."
  [graph x y]
  (contains? (dependencies graph x) y))

(defn dependent
  "True if y is a dependent of x."
  [graph x y]
  (contains? (dependents graph x) y))

(defn- add-relationship [graph key x y]
  (update-in graph [key x] union #{y}))

(defn depend
  "Adds to the dependency graph that x depends on deps. Forbids circular dependencies."
  ([graph x] graph)
  ([graph x dep]
     {:pre [(not (depends? graph dep x))]}
     (-> graph
         (add-relationship :dependencies x dep)
         (add-relationship :dependents dep x)))
  ([graph x dep & more]
     (reduce (fn [g d] (depend g x d))
             graph (cons dep more))))

(defn- remove-from-map [amap x]
  (reduce (fn [m [k vs]]
            (assoc m k (disj vs x)))
          {} (dissoc amap x)))

(defn remove-all
  "Removes all references to x in the dependency graph."
  ([graph] graph)
  ([graph x]
     (assoc graph
       :dependencies (remove-from-map (:dependencies graph) x)
       :dependents (remove-from-map (:dependents graph) x)))
  ([graph x & more]
     (reduce remove-all
             graph (cons x more))))

(defn remove-key
  "Removes the key x from the dependency graph without removing x as a depedency of other keys."
  ([graph] graph)
  ([graph x]
     (assoc graph
       :dependencies (dissoc (:dependencies graph) x)))
  ([graph x & more]
     (reduce remove-key
             graph (cons x more))))
