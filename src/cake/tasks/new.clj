(ns cake.tasks.new
  (:use cake cake.ant
        [cake.project :only [group]]
        [useful.io :only [extract-resource]])
  (:import [org.apache.tools.ant.taskdefs Mkdir]))

(defn project-contents [project]
  (format
"(defproject %s \"0.0.0-SNAPSHOT\"
  :description \"TODO: add summary of your project\"
  :dependencies [[clojure \"1.2.0-master-SNAPSHOT\"]])
" project))

(deftask new
  "Create scaffolding for a new project."
  (let [project (symbol (first (:new opts)))
        name    (name project)
        group   (group project)]
    (ant Mkdir {:dir name})
    (ant Mkdir {:dir (str name "/src")})
    (ant Mkdir {:dir (str name "/test")})
    (spit (str name "/project.clj") (project-contents project))
    (log "Created file: project.clj")
    (spit (str name "/.gitignore")
          (apply str (interleave [".cake" "pom.xml" "*.jar" "*.war" "lib" "classes" "build"] (repeat "\n"))))
    (log "Created file: .gitignore")
    (extract-resource "LICENSE" name)
    (log "Created default LICENSE file (Eclipse Public License)")))
