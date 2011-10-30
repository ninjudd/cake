(ns cake.utils
  (:use cake
        [cake.file :only [file touch mtime]]
        [uncle.core :only [ant args argline]]
        [bake.core :only [os-name]]
        [clojure.java.io :only [writer]])
  (:import (org.apache.tools.ant.taskdefs ExecTask)))

(def *readline-marker* nil)

(defn prompt-read [prompt & {:keys [echo]}]
  (let [echo (if (false? echo) "@" "")]
    (println (str echo *readline-marker* prompt))
    (read-line)))

(defn yes-or-no [prompt & opts]
  (#{"Y" "y"} (apply prompt-read (str prompt " [y/N]") opts)))

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

(defn keepalive! []
  (touch *pidfile*))

(defn start-watchdog! []
  (when-let [timeout (get *config* "jvm.auto-shutdown")]
    (let [timeout (* 1000 (Integer. timeout))]
      (future-call
       (fn []
         (let [idle (- (System/currentTimeMillis) (mtime *pidfile*))]
           (when (> idle timeout)
             (doseq [out [*console* *out*]]
               (binding [*out* out]
                 (println (format "[%tc] -- auto shutdown after %d idle seconds"
                                  (System/currentTimeMillis)
                                  (int (/ idle 1000))))))
             (System/exit 1))
           (Thread/sleep (- timeout idle))
           (recur)))))))

(defn to-vec
  "If s is not a collection, wrap it in a vector. Otherwise, make it
   a vector."
  [s] (if (coll? s) (vec s) [s]))