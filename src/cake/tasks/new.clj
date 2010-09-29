(ns cake.tasks.new
  (:use cake cake.core cake.ant
        [cake.project :only [group]]
        [clojure.string :only [join]]
        [cake.utils.io :only [extract-resource]])
  (:import [org.apache.tools.ant.taskdefs Mkdir Copy]))

(def default-template
"(defproject +project+ \"0.0.1-SNAPSHOT\"
  :description \"TODO: add summary of your project\"
  :dependencies [[clojure \"1.2.0\"]])
")

(def template-dir (file "~" ".cake" "templates"))
(def default-template-dir (file template-dir "default"))

(defn rename-dirs [root project]
  (log "Renaming directories with +project+ in their name")
  (doseq [old-file (reverse (sort-by (comp count str) (file-seq root)))
          :let [name (.getName old-file)
                replaced (.replace name "+project+" project)]
          :when (not= name replaced)]
    (.renameTo old-file (file (.getParent old-file) replaced))))

(defn template-new [project template]
  (let [root (file *pwd* project)]
    (if (.exists root)
      (println "error:" project "already exists in this directory")
      (do
        (log (str "Creating a new project based on ~/cake/templates/" template))
        (ant Copy {:todir root} (add-fileset {:dir (str (file template-dir template))}))
        (rename-dirs root project)
        (let [files (file-seq root)]
          (log (str "Replacing +project+ with '" project "' in all files."))
          (doseq [f (filter #(.isFile %) files)]
            (spit f (.replace (slurp f) "+project+" project))))))))

(defn create-default-template []
  (log "Creating template directory: ~/.cake/templates/default")
  (ant Mkdir {:dir default-template-dir})
  (ant Mkdir {:dir (file default-template-dir "src" "+project+")})
  (spit (file default-template-dir "src" "+project+" "core.clj") "(ns +project+.core)")
  (log "Created template file: src/+project+/core.clj")
  (ant Mkdir {:dir (file default-template-dir "test")})
  (spit (file default-template-dir "project.clj") default-template)
  (log "Created template file: project.clj")
  (spit (file default-template-dir ".gitignore")
        (apply str (interleave [".cake" "pom.xml" "*.jar" "*.war" "lib" "classes" "build" "+project+"] (repeat "\n"))))
  (log "Created template file: .gitignore")
  (extract-resource "LICENSE" default-template-dir)
  (log "Created template LICENSE file (Eclipse Public License)"))

(deftask new
  "Create scaffolding for a new project."
  "You can put project templates in ~/.cake/templates. Each template is a directory with a default
   template. You can specify which template to use for your project when you call this task. If
   you don't specify which template to use, ~/.cake/templates will be used. It will be created if
   it does not already exist.
   Examples: cake new aproj, cake new mytemplate aproj"
  {[one two] :new}
  (when-not (.exists default-template-dir)
    (create-default-template))
  (apply template-new (or (and two [two one]) [one "default"])))
