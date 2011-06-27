(ns cake.tasks.new
  (:use cake cake.core cake.file uncle.core
        [bake.core :only [log]]
        [cake.project :only [group]]
        [clojure.string :only [join]]
        [clojure.java.io :only [copy]]
        [bake.io :only [extract-resource]])
  (:import [org.apache.tools.ant.taskdefs Copy]))

(def default-template
"(defproject +project+ \"0.0.1-SNAPSHOT\"
  :description \"TODO: add summary of your project\"
  :dependencies [[clojure \"1.2.0\"]]
  :copy-deps true)
")

(def template-dir (file "~" ".cake" "templates"))

(defn rename-files [root project]
  (log "Renaming directories with +project+ in their name")
  (doseq [old-file (reverse (sort-by (comp count str) (file-seq root)))
          :let [name (.getName old-file)
                replaced (.replace name "+project+" (.replace project "-" "_"))]
          :when (not= name replaced)]
    (.renameTo old-file (file (.getParent old-file) replaced))))

(defn scan-replace-contents [files project]
  (log (str "Replacing +project+ with '" project "' in all files."))
  (doseq [f (filter #(.isFile %) files)]
    (spit f (.replace (slurp f) "+project+" project))))

(defn template-new [project template]
  (let [root (file *pwd* project)]
    (if (.exists root)
      (println "error:" project "already exists in this directory")
      (do
        (log (str "Creating a new project based on ~/.cake/templates/" template))
        (ant Copy {:todir root}
          (add-fileset {:dir (str (file template-dir template))}))
        (rename-files root project)
        (scan-replace-contents (file-seq root) project)))))

(defn create-template [template]
  (log (str "Creating template directory: ~/.cake/templates/" template))
  (let [template (file template-dir template)]
    (mkdir template)
    (mkdir (file template "src" "+project+"))
    (spit (file template "src" "+project+" "core.clj") "(ns +project+.core)")
    (log "Created template file: src/+project+/core.clj")
    (mkdir (file template "test"))
    (spit (file template "project.clj") default-template)
    (log "Created template file: project.clj")
    (spit (file template ".gitignore")
          (apply str (interleave [".cake" "pom.xml" "*.jar" "*.war" "lib" "classes" "build" "/+project+"] (repeat "\n"))))
    (log "Created template file: .gitignore")
    (extract-resource "LICENSE" template)
    (log "Created template LICENSE file (Eclipse Public License)")))

(deftask new
  "Create scaffolding for a new project."
  "You can put project templates in ~/.cake/templates. Each template is a directory with a default
   template. You can specify which template to use for your project when you call this task. If
   you don't specify which template to use, ~/.cake/templates/default will be used. It will be created if
   it does not already exist.
   New project based on the default template:  cake new aproj
   New project based on a specified template:  cake new mytemplate aproj
   New template based on the default template: cake new template mytemplate"
  {[one two] :new}
  (when-not (.exists (file template-dir "default"))
    (create-template "default"))
  (if (and two (= one "template"))
    (create-template two)
    (apply template-new (if two [two one] [one "default"]))))
