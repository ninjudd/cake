(ns cake.tasks.release
  (:use cake cake.ant)
  (:import (org.apache.tools.ant.taskdefs.optional.ssh Scp)))

(def *success* (atom false))

(defn- scp
  "call the scp ant task with options"
  [user to-file to-path & options]
  (let [settings {:trust true
                  :failonerror true
                  :file (.toString to-file)
                  :todir (str user "@" to-path)}
        options (into settings options)]
    (ant Scp options)))


;check here, can probably get rid of the when-let wierdness if you make these fns return a string
(comment defn- get-key-setting
  "look for a keyfile specified in options or config"
  [& [env]]
  (when-let [path (or (first (or (map #(get opts %) [:i :identity])))
                      (when env (get config (str env ".keyfile"))))]
    [(file path)]))

(defn- get-key-setting
  "look for a keyfile specified in options or config"
  [& [env]]
  (or (seq (filter #(not (nil? %))
                   (map #(-> % opts first)
                        [:i :identity])))
      (seq (when env (get config (str env ".keyfile"))))))

(defn- detect-keys
  "look for id_rsa, id_dsa, or identity in path"
  [path]
  (seq (filter #(.exists %)
               (map #(file (java.io.File. path) ".ssh" %)
                    ["id_rsa" "id_dsa" "identity"]))))

(defn- find-keys [& [env]]
  (let [keys (or (let [x (get-key-setting env) foo (println "x:" x)] x)
                 (detect-keys (System/getProperty "user.home")))]
    (when keys (apply println "Using keyfiles:" (map #(-> % .toString .trim) keys)))
    keys))

(defn- transfer
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

(defn- merge-settings [{env :env :as settings}]
  (let [defaults {:user (or (get config (str env ".user"))
                            (System/getProperty "user.name"))
                  :keys (find-keys env)
                  :passphrase (when (:p opts) (prompt-read "Enter passphrase"))}]
    (merge defaults settings)))

(defn- deploy*
  [settings]
  (let [{user :user source :source hosts :hosts path :path keys :keys passphrase :passphrase} (merge-settings settings)]
    (doseq [host hosts]
      (let [dest (str host ":" path)]
        (println "Attempting to copy:" (.toString source) "to" dest)
        (transfer user source dest keys passphrase)
        (when-not @*success*
          (when (and keys (not (:p opts)))
            (println "All keyfiles failed, if a keyfile passphrase is required, use the -p flag"))
          (transfer user source dest))))))

(defn deploy [& args]
  (if (vector? (first args))
    (let [hosts (first args)
          settings (apply hash-map (rest args))]
      (deploy* (assoc settings :hosts hosts)))
    (let [settings (apply hash-map args)]
      (deploy* settings))))
