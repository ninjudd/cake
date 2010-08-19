(ns cake.tasks.new
  (:use cake cake.core cake.ant
        [cake.project :only [group]]
        [useful.io :only [extract-resource]])
  (:import [org.apache.tools.ant.taskdefs Mkdir]))

(defn project-contents [project]
  (format
"(defproject %s \"0.0.0-SNAPSHOT\"
  :description \"TODO: add summary of your project\"
  :dependencies [[clojure \"1.2.0\"]])
" project))

(deftask new
  "Create scaffolding for a new project."
  (let [project (symbol (first (:new *opts*)))
        name    (name project)
        group   (group project)
        root    (str *pwd* "/" name)]
    (println *pwd*)
    (ant Mkdir {:dir root})
    (ant Mkdir {:dir (str root "/src")})
    (ant Mkdir {:dir (str root "/test")})
    (spit (str root "/project.clj") (project-contents project))
    (log "Created file: project.clj")
    (spit (str root "/.gitignore")
          (apply str (interleave [".cake" "pom.xml" "*.jar" "*.war" "lib" "classes" "build"] (repeat "\n"))))
    (log "Created file: .gitignore")
    (extract-resource "LICENSE" root)
    (log "Created default LICENSE file (Eclipse Public License)")))
