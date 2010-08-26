(ns cake.tasks.new
  (:use cake cake.core cake.ant
        [cake.project :only [group]]
        [clojure.string :only [join]]
        [useful.io :only [extract-resource]])
  (:import [org.apache.tools.ant.taskdefs Mkdir]))

(def default-template
"(defproject %s \"0.0.1-SNAPSHOT\"
  :description \"TODO: add summary of your project\"
  :dependencies [[clojure \"1.2.0\"]])
")

(defn project-contents [project]
  (let [template (file "~" ".cake" "project.template.clj")]
    (format (if (.exists template) (slurp template) default-template) project)))

(deftask new
  "Create scaffolding for a new project."
  "You can put a default project template in ~/.cake/project.template.clj with %s in place of the project name."
  (let [project (symbol (first (:new *opts*)))
        name    (name project)
        root    (file *pwd* name)]
    (if (.exists root)
      (println "error:" name "already exists in this directory")
      (do (ant Mkdir {:dir root})
          (ant Mkdir {:dir (file root "src")})
          (ant Mkdir {:dir (file root "test")})
          (spit (file root "project.clj") (project-contents project))
          (log "Created file: project.clj")
          (spit (file root ".gitignore")
                (apply str (interleave [".cake" "pom.xml" "*.jar" "*.war" "lib" "classes" "build"] (repeat "\n"))))
          (log "Created file: .gitignore")
          (extract-resource "LICENSE" root)
          (log "Created default LICENSE file (Eclipse Public License)")))))
