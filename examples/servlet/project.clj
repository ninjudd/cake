(defproject servlet "0.1.0-SNAPSHOT"
  :description "cake servlet example"
  :dependencies [[org.clojure/clojure "1.2.0"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [clojure-useful "0.2.6"]
                 [jline "0.9.94"]]
  :dev-dependencies [[swank-clojure "1.2.1"]]
  :aot [servlet]
  :war-files [["src/database.properties" "WEB-INF/classes/db.properties"]
              "doc"]
  :jar-files [["src/database.properties" "db.properties"]
              ["project.clj" "META-INF/project.clj"]
              "project.clj"
              "doc"])

(defcontext qa
  :deploy {:username "judd"
           :hosts ["qa1.foolambda.com" "qa2.foolambda.com"]
           :files [["foo.html" "/var/www/"]
                   ["foo.conf" "/etc/"]]
           :commands ["/etc/init.d/httpd reload"]})

(defcontext dev
  :deploy {:username "justin"
           :hosts ["localhost"]
           :files [["resources/foo.html" "."]
                   ["resources/foo.conf" "."]]
           :commands ["touch /tmp/foo"]})
