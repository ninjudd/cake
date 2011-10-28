(ns cake.tasks.search
  (:use cake
        cake.core
        [bake.core :only [log]]
        [clojure.string :only [join]])
  (:require [sherlock.core :as sherlock]))

(defn select-repos [repos]
  (let [repositories (:repositories *project*)]
    (if (seq repos)
      (select-keys repositories repos)
      repositories)))

(defn download-index [id url]
  (when (sherlock/download-needed? url)
    (log (str "Downloading index for " id ". This might take a bit."))
    (sherlock/update-index url)))

(defn identifier [{:keys [group-id artifact-id]}]
  (if group-id
    (str group-id "/" artifact-id)
    artifact-id))

(defn print-results [id page results]
  (when (seq results)
    (println " == Results from" id "-" "Showing page" page "/"
             (-> results meta :_total-hits (/ sherlock/*page-size*) Math/ceil int) "total")
    (doseq [{:keys [version description] :as artifact} results]
      (println (str "[" (identifier artifact) " \"" version "\"]") description))
    (prn)))

(deftask search
  "Search maven repos for artifacts."
  "Search takes a query that is treated as a lucene search query,
   allowing for simple string matches as well as advanced lucene queries.
   The first time you run this task will take a bit as it has to fetch the
   lucene indexes for your maven repositories.
   Options:
     --page   -- The page number to fetch (each page is 10 results long).
     --repos  -- ids of repositories to search delimited by commas."
  {[page] :page repos :repos search :search}
  (let [page (if page (Integer. page) 1)]
    (binding [sherlock/*page-size* 10]
      (doseq [[id url] (select-repos repos)]
        (download-index id url)
        (print-results
         id page
         (sherlock/get-page page (sherlock/search url (join " " search) page)))))))

(deftask update-repos
  "Update maven lucene indexes."
  "If passed arguments, they are treated as the name of maven repos to update.
   Otherwise, update all repos."
  {:keys [update-repos]}
  (doseq [[id url] (select-repos update-repos)]
    (log (str "Updating index for " id ". This might take a bit."))
    (sherlock/update-index url)))

(deftask latest-version
  "Find the latest version of an artifact."
  "Pass the name of a dependency and cake will search the lucene indexes for it
   and return the latest version. You can pass --repos which is expected to be a
   comma delimited list of repository names. If it is not passed, all repositories
   will be searched."
  {[term] :latest-version, repos :repos}
  (let [repos (select-repos repos)
        [group-id artifact-id] (.split term "/")
        query (if artifact-id
                (str "g:" group-id " AND a:" artifact-id)
                (str "g:" group-id " AND a:" group-id))]
    (doseq [[id url] repos] (download-index id url))
    (let [result (first (sort-by :version (comp unchecked-negate compare)
                                 (filter (comp #{term} identifier)
                                         (mapcat #(sherlock/search % query 10 Integer/MAX_VALUE)
                                                 (vals repos)))))]
      (println
       (if result
         (str "[" (identifier result) " " (:version result) "]")
         (println "Nothing was found."))))))