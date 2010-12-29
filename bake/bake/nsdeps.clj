;; by Stuart Sierra, http://stuartsierra.com/
;; December 1, 2010

;; Copyright (c) Stuart Sierra, 2010. All rights reserved.  The use
;; and distribution terms for this software are covered by the Eclipse
;; Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this
;; distribution.  By using this software in any fashion, you are
;; agreeing to be bound by the terms of this license.  You must not
;; remove this notice, or any other, from this software.

;; copied from lazytest.nsdeps

(ns bake.nsdeps
  "Parsing namespace declarations for dependency information."
  (:use [clojure.set :only (union)]
        [bake.find-namespaces :only [find-clojure-sources-in-dir read-file-ns-decl]]
        [bake.dependency :only [depend dependents remove-key depends?]]))

(defn- prepend [prefix sym]
  (symbol (str (when prefix (str prefix "."))
               sym)))

(defn- deps-from-libspec [prefix form]
  (cond (coll? form) (if (or prefix (some keyword? form) (= 1 (count form)))
                       (deps-from-libspec prefix (first form))
                       (apply union (map #(deps-from-libspec (prepend prefix (first form)) %)
                                         (rest form))))
        (symbol? form) #{(prepend prefix form)}
        (keyword? form) #{}
        :else (throw (IllegalArgumentException.
                      (pr-str "Unparsable namespace form:" form)))))

(defn- deps-from-ns-form [form]
  (when (and (list? form)
	     (contains? #{:use :require} (first form)))
    (apply union (map #(deps-from-libspec nil %) (rest form)))))

(defn deps-from-ns-decl
  "Given a (quoted) ns declaration, returns a set of symbols naming
  the dependencies of that namespace.  Handles :use and :require clauses."
  [decl]
  (apply union (map deps-from-ns-form decl)))

;; moved from lazytest.tracker

(defn find-sources [dirs]
  (mapcat find-clojure-sources-in-dir dirs))

(defn newer-than [timestamp files]
  (filter #(>= (quot (.lastModified %) 1000)
               (quot timestamp         1000))
          files))

(defn newer-namespace-decls [timestamp dirs]
  (remove nil? (map read-file-ns-decl (newer-than timestamp (find-sources dirs)))))

(defn- add-to-dep-graph [dep-graph namespace-decls]
  (reduce (fn [g decl]
            (let [nn (second decl)
                  deps (deps-from-ns-decl decl)]
              (apply depend g nn deps)))
          dep-graph namespace-decls))

(defn- remove-from-dep-graph [dep-graph new-decls]
  (apply remove-key dep-graph (map second new-decls)))

(defn update-dependency-graph [dep-graph new-decls]
  (-> dep-graph
      (remove-from-dep-graph new-decls)
      (add-to-dep-graph new-decls)))

(defn affected-namespaces [changed-namespaces dep-graph]
  (apply union
         (set changed-namespaces)
         (map #(dependents dep-graph %)
              changed-namespaces)))