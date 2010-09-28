(ns cake.tasks.new
  (:use cake cake.core cake.ant
        [cake.project :only [group]]
        [clojure.string :only [join]]
        [cake.utils.io :only [extract-resource]])
  (:import [org.apache.tools.ant.taskdefs Mkdir Copy]))

(def default-template
"(defproject %s \"0.0.1-SNAPSHOT\"
  :description \"TODO: add summary of your project\"
  :dependencies [[clojure \"1.2.0\"]])
")

(defn template-new [project]
  (let [root (file *pwd* project)]
    (if (.exists root)
      (println "error:" project "already exists in this directory")
      (do
        (log "Creating a new project based on ~/.cake/template")
        (ant Copy {:todir root} (add-fileset {:dir (str (file "~" ".cake" "template"))}))
        (let [files (file-seq root)
              rep (filter identity
                          (map #(let [name (.getName %)
                                      replaced (.replace name "+project+" project)]
                                  (when (not= replaced name) [% (file (.getParent %) replaced)]))
                               files))]
          (log "Renaming directories with +project+ in their name")
          (doseq [[from to] (sort-by (comp count str second) #(or (and (= % %2) 0) 1) rep)]
            (.renameTo from to))
          (let [files (file-seq root)]
            (log (str "Replacing +project+ with '" project "' in all files."))
            (doseq [f (filter #(.isFile %) files)]
              (spit f (.replace (slurp f) "+project+" project)))))))))

(deftask new
  "Create scaffolding for a new project."
  "You can put a default project template in ~/.cake/template. Substitute +project+ anywhere
   that you want your project name to be."
  [{[project] :new}]
  (let [template-dir (file "~" ".cake" "template")]
    (if (.exists template-dir)
      (template-new project)
      (do
        (log "Creating template directory: ~/.cake/template")
        (ant Mkdir {:dir template-dir})
        (ant Mkdir {:dir (file template-dir "src" "+project+")})
        (spit (file template-dir "src" "+project+" "core.clj") "(ns +project+.core)")
        (log "Created template file: src/+project+/core.clj")
        (ant Mkdir {:dir (file template-dir "test")})
        (spit (file template-dir "project.clj") (format default-template project))
        (log "Created template file: project.clj")
        (spit (file template-dir ".gitignore")
              (apply str (interleave [".cake" "pom.xml" "*.jar" "*.war" "lib" "classes" "build" project] (repeat "\n"))))
        (log "Created template file: .gitignore")
        (extract-resource "LICENSE" template-dir)
        (log "Created template LICENSE file (Eclipse Public License)")
        (template-new project)))))
