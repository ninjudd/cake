(ns cake.tasks.search
  (:use cake
        cake.core
        [bake.core :only [log]]
        [clojure.string :only [join]])
  (:require [sherlock.core :as sherlock]))

(defn- print-results [id page results]
  (when (seq results)
    (println " == Results from" id "-" "Showing page" page "/"
             (-> results meta :_total-hits (/ sherlock/*page-size*) Math/ceil int) "total")
    (doseq [{:keys [group-id artifact-id version description]} results]
      (println (str "[" (if group-id
                          (str group-id "/" artifact-id)
                          artifact-id)
                    " \"" version "\"]")
               description))
    (prn)))

(deftask search
  "Search maven repos for artifacts."
  "Search takes a query that is treated as a lucene search query,
   allowing for simple string matches as well as advanced lucene queries.
   The first time you run this task will take a bit as it has to fetch the
   lucene indexes for your maven repositories.
   Options:
     --update -- Redownload (update) your existing lucene indexes.
     --page   -- The page number to fetch (each page is 10 results long).
     --repos  -- ids of repositories to search delimited by commas."
  {update :update [page] :page repos :repos search :search}
  (let [repositories (:repositories *project*)
        repos (if repos (select-keys repositories repos) repositories)
        page (if page (Integer. page) 1)]
    (binding [sherlock/*page-size* 10]
      (doseq [[id url] repos]
        (when (or update (sherlock/download-needed? url))
          (log "Downloading index for" id)
          (sherlock/update-index url))
        (print-results
         id page
         (sherlock/get-page page (sherlock/search url (join " " search) page)))))))