(ns cake.tasks.release
  (:use cake cake.core cake.ant)
  (:import (org.apache.tools.ant.taskdefs.optional.ssh Scp SSHExec)
           (com.jcraft.jsch JSch)
           (java.io ByteArrayInputStream ByteArrayOutputStream)
           (javax.security.auth.login FailedLoginException)))

(def *credentials* {})

(defn find-keys []
  (let [keys (or (seq (map file (filter #(not (nil? %))
                                        (map #(-> % *opts* first)
                                             [:i :identity]))))
                 (seq (filter #(.exists %)
                              (map #(file (java.io.File. (System/getProperty "user.home")) ".ssh" %)
                                   ["id_rsa" "id_dsa" "identity"]))))]
    (when keys
      (print "\n")
      (apply println "Trying keyfiles:" (map #(-> % .toString .trim) keys))
      (print "\n"))
    keys))

(defn scp [source dest settings]
  (ant Scp (merge {:trust true
                   :file (if (instance? java.io.File source)
                           (.toString source)
                           source)
                   :todir (str (:username *credentials*) "@" (:host settings) ":" dest)}
                  settings
                  *credentials*)))

(defn ssh [command settings]
  (ant SSHExec (merge {:trust true, :command command}
                      settings
                      *credentials*)))

(defn try-keyfile [host user key & [passphrase]]
  (try
    (println (str "trying " key))
    (ssh "dir" {:host host
                :username user
                :keyfile key
                :passphrase passphrase})
    (println (str key " returning true"))
    true
    (catch org.apache.tools.ant.BuildException e
      nil)))

(defn try-password [host user]
  (println "All keyfiles failed, if a keyfile passphrase is required, use the -p flag\n")
  (println "attempting password authentication...\n")
  (let [password (prompt-read "password" :echo false)]
    (try
      (ssh "dir" {:host host
                  :username user
                  :password password})
      password)))

(defn get-credentials [host user & args]
  (let [passphrase (when (:p *opts*) (prompt-read "passphrase" :echo false))]
    (loop [keys (first args)]
      (if (try-keyfile host user (first keys) passphrase)
        {:username user, :keyfile (first keys), :passphrase passphrase}
        (if (seq (rest keys))
          (recur (rest keys))
          {:username user, :password (try-password host user)})))))

(defmacro with-credentials [credentials & forms]
  `(binding [*credentials* ~credentials]
     ~@forms))
