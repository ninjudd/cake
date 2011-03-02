(ns cake.tasks.clojars
  (:require [clojure.string :as s]
            [clojure.java.io :as io])
  (:use cake cake.core [bake.core :only [log]]))

(def clojars-repo-url "http://clojars.org/repo")
(def clojars-all-jars-url (str clojars-repo-url "/all-jars.clj"))
(def clojars-all-poms-url (str clojars-repo-url "/all-poms.txt"))

(defn http-get-text-seq [url] (line-seq (io/reader url)))

(defn get-latest-version [library-name]
  (let [response (http-get-text-seq clojars-all-jars-url)
	lib-name (symbol library-name)]
    (second (last (filter #(= (first %) lib-name)
			  (for [line response]
			    (read-string line)))))))

(defn clojars-install
  ([library-name dev]
     (if-let [version (get-latest-version library-name)]
       (clojars-install library-name version dev)
       (println "Can't find version of" library-name "on clojars.org\r\n"
                "If the library is in another repository, please provide a version argument.")))
  ([library-name library-version dev]
     (let [s-to (if dev #{":dev-deps" ":dev-dependencies"} #{":dependencies" ":deps"})
           project (line-seq (io/reader "project.clj"))]
       (if-let [dep-line (some
                          (fn [x] (and (some #(re-find (re-pattern %) x) s-to) x))
                          project)]
         (let [to (some s-to (.split dep-line " "))
               pad (and dep-line (-> dep-line (s/split #"\[" 2) first count inc))]
           (spit "project.clj"
                 (s/replace (slurp "project.clj")
                            (re-pattern (str to "\\s*\\["))
                            (str to " [[" library-name
                                 " \"" library-version "\"]"
                                 (when-not (re-find #"\[\]" dep-line)
                                   (str "\n" (s/join (repeat pad " ")))))))
           (log "Added" library-name library-version "to your" (str to ".")))
         (println "Neither" (first s-to) "nor" (last s-to) "were found in your project.clj.")))))

(defn clojars-search [term]
  (let [response (http-get-text-seq clojars-all-jars-url)]
    (println "\n\nLibraries on Clojars.org that contain the term: " term)
    (println "--------------------------------------------------------------------------------")
    (doseq [entry (for [line response :when (.contains line term)]
		    (read-string line))]
      (println "  " (first entry) "  " (second entry)))
    (println "\n\n")))

(defn clojars-versions [library-name]
  (let [response (http-get-text-seq clojars-all-jars-url)]
    (println "\n\nAvailable versions for library: " library-name)
    (println "--------------------------------------------------------------------------------")
    (doseq [entry (filter #(= (first %) (symbol library-name))
			  (for [line response]
			    (read-string line)))]
      (println "  " (first entry) "  " (second entry)))
    (println "\n\n")))

(defn get-pom-dir
  ([library-name version]
     (let [id-str (if (.contains library-name "/")
		    (str "./" library-name "/" version "/")
		    (str "./" library-name "/" library-name "/" version "/"))]
       id-str)))

(defn get-pom-locations
  ([library-name version]
     (let [response (http-get-text-seq clojars-all-poms-url)
	   pom-dir (get-pom-dir library-name version)]
       (for [line response :when (.startsWith line pom-dir)]
	 line))))

(defn get-latest-pom-location
  ([library-name version]
     (last (get-pom-locations library-name version))))

(defn to-clojars-url
  ([file-location]
     (str clojars-repo-url "/" file-location)))

(defn get-latest-pom-file
  ([library-name version]
     (http-get-text-seq (to-clojars-url (get-latest-pom-location library-name version)))))

(defn extract-description-text [xml]
  (when-let [desc (re-find (re-pattern (str "<description>(.*)</description>")) xml)]
    (second desc)))

(defn description-text [xml-seq]
  (-> (apply str
	     (for [line xml-seq
		   :when (.contains line "<description>")]
	       line))
      (extract-description-text)))

(defn get-library-dependencies
  [library-name version]
  (let [pom-xml (clojure.xml/parse
		 (java.io.ByteArrayInputStream.
		  (.getBytes (apply str (get-latest-pom-file library-name version)))))
	deps-xml (filter #(= (:tag %) :dependencies) (:content pom-xml))
	deps-seq (partition 3 (for [x (xml-seq deps-xml)
				    :when (or (= (:tag x) :artifactId)
					      (= (:tag x) :groupId)
					      (= (:tag x) :version))]
				(hash-map (:tag x) (first (:content x)))))]
    (into #{} (for [v deps-seq] (apply merge v)))
    (map :content (:content (first deps-xml)))))

(defn print-library-dependencies
  [library-name version]
  (println (str "\n\nDependencies for: " library-name "  " version))
  (println "--------------------------------------------------------------------------------")
  (doseq [d (get-library-dependencies library-name version)]
    (let [dep (apply merge (map #(hash-map (:tag %) (first (:content %))) d))]
      (println (str (:groupId dep) "/" (:artifactId dep) "  " (:version dep)))))
  (println "\n\n"))

(defn clojars-describe
  ([library-name]
     (clojars-describe library-name (get-latest-version library-name)))
  ([library-name version]
     (let [pom-xml-seq (get-latest-pom-file library-name version)
	   desc-text (description-text pom-xml-seq)]
       (println (str "\n\nDescription for library: " library-name "  " version))
       (println "--------------------------------------------------------------------------------")
       (println desc-text)
       (println "")
       (when desc-text
	 (print-library-dependencies library-name version)))))

(deftask dependencies
  "Look up the dependencies of a library on clojars."
  {[library-name version] :dependencies}
  (if (nil? library-name)
    (println "Usage: cake dependencies <library-name> [version]")
    (print-library-dependencies library-name version)))

(deftask describe
  "Describe a library on clojars."
  {[library-name] :describe}
  (if (nil? library-name)
    (println "Usage: cake describe <library-name>")
    (clojars-describe library-name)))

(deftask versions
  "List all available versions of a library on clojars."
  {[library-name] :versions}
  (if (nil? library-name)
    (println "Usage: cake versions <library-name>")
    (clojars-versions library-name)))

(deftask search
  "Search clojars.org for libraries."
  {terms :search}
  (if (nil? terms)
    (println "Usage: cake search <terms>")
    (->> terms (s/join " ") clojars-search)))

(deftask add-dep
  "Install a library from into your project."
  "The library will be added to the :deps or :dependencies vector in your project.clj.
   If you pass the --dev option, it'll be added to :dev-deps or :dev-dependencies."
  {[library-name version] :add-dep [dev] :dev}
  (if (nil? library-name)
    (println "Usage: cake add-dep <library-name> [version] [--dev]")
    (if version
      (clojars-install library-name version dev)
      (clojars-install library-name dev))))
