(ns cake.tasks.release
  (:use cake cake.core
        [cake.tasks.jar :only [jarfile warfile]]
        [clojure.java.io :only [copy]]
        [useful :only [verify assoc-or]]
        [cake.ant :only [log]])
  (:import [com.jcraft.jsch JSch ChannelExec Logger UserInfo JSchException]
           [java.io FileInputStream]))

(def *session*      nil)
(def *session-opts* nil)

(defn keyfiles []
  (if-let [id (or (first (:identity *opts*)) (*config* "release.identity"))]
    (list (file id))
    (for [id (list "id_rsa" "id_dsa" "identity") :let [keyfile (file "~/.ssh" id)] :when (.exists keyfile)]
      keyfile)))

(defn- log-auth [username host keyfile message]
  (when (verbose?)
    (let [auth (if keyfile (.getPath keyfile) "password")]
      (log (format "Authenticating %s@%s using %s: %s" username host auth message)))))

(defn- session-connect [{:keys [host port username password passphrase keyfile] :as opts}]
  (let [jsch (JSch.)]
    (JSch/setLogger
     (proxy [Object Logger] []
       (isEnabled [level] (debug?))
       (log [level message] (println message))))
    (when keyfile (.addIdentity jsch (.getPath keyfile)))
    (try (let [username (or username (System/getProperty "user.name"))
               session  (.getSession jsch username host (or port 22))]
           (doto session
             (.setUserInfo
              (proxy [Object UserInfo] []
                (getPassword      []  password)
                (getPassphrase    []  passphrase)
                (promptYesNo      [_] true)
                (promptPassword   [_] true)
                (promptPassphrase [_] true)
                (showMessage      [_])))
             (.connect))
           (set! *session-opts* opts)
           (log-auth username host keyfile "Auth success")
           session)
         (catch JSchException e           
           (log-auth username host keyfile (.getMessage e))
           nil))))

(defn- prompt-passphrase [keyfile]
  (or (:passphrase *session-opts*)
      (prompt-read (format "Enter passphrase for key '%s'" (.getPath keyfile)) :echo false)))

(defn- prompt-password []
  (or (:password *session-opts*)
      (prompt-read "Password" :echo false)))

(defn- start-session [{:keys [host port username] :as opts}]
  (or (first (remove nil?
        (for [keyfile (keyfiles)]
          (let [opts (assoc opts :keyfile keyfile)
                session (session-connect opts)]
            (or session
                (session-connect (assoc opts :passphrase (prompt-passphrase keyfile))))))))
      (session-connect (assoc opts :password (prompt-password)))
      (throw (Exception. (format "unable to start session to %s@%s" username host)))))

(defn ssh-session* [opts f]
  (let [hosts (or (:hosts opts) [(:host opts)])
        opts  (dissoc opts :hosts)]
    (binding [*session-opts* nil]
      (doseq [opts (map (partial assoc opts :host) hosts)]
        (binding [*session* (start-session opts)]
          (try (f)
               (finally (.disconnect *session*))))))))

(defmacro ssh-session [opts & forms]
  `(ssh-session* ~opts (fn [] ~@forms)))

(defn- wait-for-ack [in]
  (let [code (.read in)]
    (when-not (= 0 code)
      (copy in *outs*)
      (throw (Exception. (case code 1 "ssh error" 2 "ssh fatal error" -1 "disconnect error" "unknown error"))))))

(defn- send-ack [out]
  (.write out (byte-array [(byte 0)]))
  (.flush out))

(defn ssh-exec [command f]
  (let [channel (.openChannel *session* "exec")]
    (.setCommand channel command)
    (let [in  (.getInputStream channel)
          out (.getOutputStream channel)
          ext (.getExtInputStream channel)]
      (.connect channel)
      (wait-for-ack in)
      (f in out ext)
      (.disconnect channel))))

(defn upload
  ([src] (upload src "."))
  ([src dest]
      (let [src (if (sequential? src) src [src])]
        (ssh-exec (str "scp -r -d -t " (or dest "."))
          (fn [in out ext]
            (doseq [f src :let [f (file f)]]
              (log "uploading" (.getAbsolutePath f) "to" (:host *session-opts*))
              (let [command (format "C0644 %d %s\n" (.length f) (.getName f))]
                (.write out (.getBytes command))
                (.flush out)
                (wait-for-ack in))
              (with-open [fs (FileInputStream. f)]
                (copy fs out :buffer-size 8192))
              (send-ack out)
              (wait-for-ack in))
            (.close out)
            (copy ext *outs*))))))

(defn upload-to-clojars [jarfile]  
  (log "Releasing jar:" jarfile)
  (ssh-session {:host "clojars.org" :username "clojars"}
    (upload ["pom.xml" jarfile])))

(deftask release #{jar}
  "Release project jar to clojars."
  (upload-to-clojars (jarfile)))

(deftask deploy #{war}
  "Deploy war to a group of servers."
  "This deploys a plain war by default. To deploy an uberwar, add '(deftask deploy #{uberwar})' to project.clj."
  (if (:deploy *project*)
    (let [group  (symbol (or (first (:deploy *opts*)) 'qa))
          deploy (group (:deploy *project*))]
      (verify deploy (str "no deploy options specified for" group))
      (log "Deploying to" group)
      (ssh-session deploy
        (upload [(warfile)] (:dest deploy))))
    (println "no :deploy key in project.clj")))
