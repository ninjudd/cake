(ns cake.utils
  (:use cake
        [cake.file :only [file]]
        [cake.ant :only [ant args argline]]
        [clojure.java.io :only [copy]])
  (:import [org.apache.tools.ant.taskdefs ExecTask]
           [java.net JarURLConnection]
           [java.io File FileInputStream]))

(def *readline-marker* nil)

(defn prompt-read [prompt & opts]
  (let [opts (apply hash-map opts)
        echo (if (false? (:echo opts)) "@" "")]
    (println (str echo *readline-marker* prompt))
    (read-line)))

(defn os-name []
  (let [name (System/getProperty "os.name")]
    (condp #(.startsWith %2 %1) name
      "Linux"    "linux"
      "Mac OS X" "macosx"
      "SunOS"    "solaris"
      "Windows"  "windows"
      "unknown")))

(defn os-arch []
  (or (first (:arch *opts*))
      (get *config* "project.arch")
      (let [arch (System/getProperty "os.arch")]
        (case arch
          "amd64" "x86_64"
          "i386"  "x86"
          arch))))

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

(defn resource-stream [name]
  (if-let [url (.findResource (.getClassLoader clojure.lang.RT) name)]
    (let [conn (.openConnection url)]
      (if (instance? JarURLConnection conn)
        (let [jar (cast JarURLConnection conn)]
          (.getInputStream jar))
        (FileInputStream. (File. (.getFile url)))))))

(defn extract-resource [name dest-dir]
  (if-let [s (resource-stream name)]
    (let [dest (File. dest-dir name)]
      (.mkdirs (.getParentFile dest))
      (copy s dest)
      dest)
    (throw (Exception. (format "unable to find %s on classpath" name)))))
