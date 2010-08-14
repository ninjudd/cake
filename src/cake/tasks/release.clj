(ns cake.tasks.release
  (:use cake cake.core cake.ant)
  (:import (org.apache.tools.ant.taskdefs.optional.ssh Scp)))

(defn scp
  "call the scp ant task with options"
  [user to-file to-path & options]
  (let [settings {:trust true
                  :failonerror true
                  :file (.toString to-file)
                  :todir (str user "@" to-path)}
        options (into settings options)]
    (ant Scp options)))

(defn- get-key-options
  "check the project options for a keyfile"
  []
  (seq (map file (filter #(not (nil? %))
                         (map #(-> % *opts* first)
                              [:i :identity])))))

(defn get-key-setting
  "look for a keyfile specified in options or config"
  [env]
  (when-let [kf (*config* (str env ".keyfile"))]
    [(file kf)]))

(defn get-keys-in
  "look for id_rsa, id_dsa, or identity in path"
  [path]
  (seq (filter #(.exists %)
               (map #(file (java.io.File. path) ".ssh" %)
                    ["id_rsa" "id_dsa" "identity"]))))

(defn find-keys [& [env]]
  (let [keys (or (get-key-options)
                 (when env (get-key-setting env))
                 (get-keys-in (System/getProperty "user.home")))]
    (when keys (apply println "Using keyfiles:" (map #(-> % .toString .trim) keys)))
    keys))

(defn try-scp
  ([user source dest keys passphrase]
     (doseq [key keys :while (not @*success*)]
       (print (.toString key) "...")
       (try
         (scp user source dest {:keyfile key :passphrase passphrase})
         (swap! *success* (fn [x] true))
         (catch org.apache.tools.ant.BuildException e
           (println "F\n")))))
  ([user source dest]
     (try 
       (scp user source dest {:password (prompt-read (str "password for " user))})
       (catch org.apache.tools.ant.BuildException e
         (println "Incorrect Password.")))))

(defn merge-settings [{env :env :as settings}]
  (let [defaults {:user (or (*config* (str env ".user"))
                            (System/getProperty "user.name"))
                  :keys (find-keys env)
                  :passphrase (when (:p *opts*) (prompt-read "Enter passphrase"))}]
    (merge defaults settings)))

(defn deploy*
  [settings]
  (let [{:keys [user source hosts path keys passphrase]} (merge-settings settings)]
    (doseq [host hosts]
      (let [dest (str host ":" path)]
        (println "Attempting to copy:" (.toString source) "to" dest)
        (try-scp user source dest keys passphrase)
        (when-not @*success*
          (when (and keys (not (:p *opts*)))
            (println "All keyfiles failed, if a keyfile passphrase is required, use the -p flag"))
          (try-scp user source dest))))))

(defn deploy [& args]
  (if (vector? (first args))
    (let [hosts (first args)
          settings (apply hash-map (rest args))]
      (deploy* (assoc settings :hosts hosts)))
    (let [settings (apply hash-map args)]
      (deploy* settings))))
