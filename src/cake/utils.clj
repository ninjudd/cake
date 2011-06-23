(ns cake.utils
  (:use cake
        [cake.file :only [file]]
        [uncle.core :only [ant args argline]]
        [bake.core :only [os-name]])
  (:import (org.apache.tools.ant.taskdefs ExecTask)))

(def *readline-marker* nil)

(defn prompt-read [prompt & opts]
  (let [opts (apply hash-map opts)
        echo (if (false? (:echo opts)) "@" "")]
    (println (str echo *readline-marker* prompt))
    (read-line)))

(defn sudo [& arglist]
  (let [password (prompt-read "Password" :echo false)
	opts {:dir *root*
	      :input-string (str password "\n")}]
    (if (= "linux" (os-name))
      (ant ExecTask (assoc opts :executable "script")
        (argline (apply str "-q -c 'sudo -S "
                        (interpose " " (conj arglist "' /dev/null")))))
      (ant ExecTask (assoc opts :executable "sudo")
        (args (apply vector "-S" arglist))))))

(defn cake-exec [& params]
  (ant ExecTask {:executable "ruby" :dir *root* :failonerror true}
    (args *script* params (str "--project=" *root*))))

(defn git [& params]
  (if (.exists (file ".git"))
    (ant ExecTask {:executable "git" :dir *root* :failonerror true}
      (args params))
    (println "warning:" *root* "is not a git repository")))

(defn ftime [string time]
  (format (apply str (map #(str "%1$t" %) string)) time))
