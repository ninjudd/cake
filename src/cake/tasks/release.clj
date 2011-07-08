(ns cake.tasks.release
  (:use cake cake.core cake.file
        [bake.core :only [verbose? debug? log]]
        [cake.tasks.jar :only [jarfile uberjarfile warfile]]
        [clojure.java.io :only [reader copy]]
        [useful.utils :only [verify]]
        [cake.utils :only [prompt-read]])
  (:import [com.jcraft.jsch JSch ChannelExec Logger UserInfo JSchException UIKeyboardInteractive]
           [java.io FileInputStream]))

(def *session*      nil)
(def *session-opts* nil)

(defn keyfiles [identity]
  (if-let [identity (or identity (first (:identity *opts*)))]
    (list (if (.startsWith identity "/")
            (file identity)
            (file "~/.ssh" identity)))
    (filter #(.exists %)
            (for [id ["id_rsa" "id_dsa" "identity"]]
              (file "~/.ssh" id)))))

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
              (proxy [Object UserInfo UIKeyboardInteractive] []
                (getPassword               []    password)
                (getPassphrase             []    passphrase)
                (promptYesNo               [_]   true)
                (promptPassword            [_]   (not (nil? password)))
                (promptPassphrase          [_]   (not (nil? passphrase)))
                (promptKeyboardInteractive [& _] (if password (into-array String [password])))
                (showMessage               [_])))
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

(defn- start-session [{:keys [host port username identity] :as opts}]
  (or (first (remove nil?
        (for [keyfile (keyfiles identity)]
          (let [opts (assoc opts :keyfile keyfile)
                session (session-connect opts)]
            (or session
                (session-connect (assoc opts :passphrase (prompt-passphrase keyfile))))))))
      (session-connect (assoc opts :password (prompt-password)))
      (abort-task (format "unable to start session to %s@%s" username host))))

(defn ssh-session* [opts & commands]
  (let [hosts (or (:hosts opts) [(:host opts)])
        opts  (dissoc opts :hosts)]
    (binding [*session-opts* nil]
      (let [sessions
            (for [host hosts]
              (start-session (assoc opts :host host)))]
        (doseq [command commands, session sessions]
          (binding [*session* session]
            (command)))
        (doseq [session sessions]
          (.disconnect session))))))

(defmacro ssh-session [opts & forms]
  `(ssh-session* ~opts ~@(map (partial list 'fn []) forms)))

(defn log-host [& message]
  (apply log (format "[%s]" (.getHost *session*)) message))

(defn copy-to-log [out]
  (let [out (reader out)]
    (loop []
      (when-let [line (.readLine out)]
        (log-host line)
        (recur)))))

(defn ssh-exec [command & [f]]
  (let [channel (.openChannel *session* "exec")]
    (.setCommand channel command)
    (let [in  (.getInputStream channel)
          out (.getOutputStream channel)
          ext (.getExtInputStream channel)]
      (.connect channel)
      (if f
        (f in out ext)
        (let [ext (reader ext)]
          (log-host command)))
      (.close out)
      (copy-to-log ext)
      (.disconnect channel))))

(defn- send-ack [out]
  (.write out (byte-array [(byte 0)]))
  (.flush out))

(defn- wait-for-ack [in]
  (let [code (.read in)]
    (when-not (= 0 code)
      (copy in *outs*)
      (throw (Exception. (case code 1 "scp error" 2 "scp fatal error" -1 "disconnect error" "unknown error"))))))

(defn upload
  ([src] (upload src "."))
  ([src dest]
     (let [src  (if (sequential? src) src [src])
           dest (or dest ".")]
        (ssh-exec (str "scp -r -d -t " (or dest "."))
          (fn [in out ext]
            (wait-for-ack in)
            (doseq [f src :let [f (file f)]]
              (log-host "uploading" (.getAbsolutePath f) "to" dest)
              (let [command (format "C0644 %d %s\n" (.length f) (.getName f))]
                (.write out (.getBytes command))
                (.flush out)
                (wait-for-ack in))
              (with-open [fs (FileInputStream. f)]
                (copy fs out :buffer-size 8192))
              (send-ack out)
              (wait-for-ack in)))))))

(defn- lookup-file [file]
  (case file
    :jar     (jarfile)
    :uberjar (uberjarfile)
    :war     (warfile)
    file))

(deftask release #{jar pom}
  "Release project jar (to clojars by default)."
  (let [release  (:release *project*)
        files    (or (:files    release) [:jar "pom.xml"])
        host     (or (:host     release) "clojars.org")
        username (or (:username release) "clojars")
        dest     (or (:dest     release) ".")]
    (when (#{"y" "Y"} (prompt-read (format "Are you sure you want to release to %s? [y/N]" host)))
      (ssh-session {:host host :username username}
        (upload (map lookup-file files) dest)))))

(deftask deploy
  "Deploy project to a group of servers."

  "Add :deploy to defproject or defcontext to enable. Also add any dependencies that need
   to be built before deploy e.g. (deftask deploy #{war}). Supported :deploy keys are:

     :hosts      [host1 host2 host3]
     :port       2222
     :username   \"username\"
     :password   \"password\"
     :identity   \"ssh-public-key\"
     :passphrase \"ssh-key-passphrase\"
     :pre  [\"echo pre-upload commands\"
            \"/etc/init.d/server stop\"]
     :copy [[\"file1\" \"file2\" \"/dest\"]
            [:war \"/another/dest\"]
            [:jar :uberjar \"/home/username\"]]
     :post [\"echo remote commands\"
            \"/etc/init.d/server start\"]

   Optionally, a list of specific nodes to deploy to may be specified, along with a context:
     cake deploy server-001 server-002 @production"
  (if (:deploy *project*)
    (let [deploy  (:deploy *project*)
          nodes   (seq (:deploy *opts*))
          deploy  (if nodes (assoc deploy :hosts nodes) deploy)
          context (:context *project*)]
      (verify deploy (str "no deploy options specified for context" context))
      (log "Deploying to" context)
      (when nodes (log "nodes:" nodes))
      (ssh-session deploy
        (doseq [command (:pre deploy)]
          (ssh-exec command))
        (doseq [files (:copy deploy)]
          (let [dest  (last files)
                files (map lookup-file (butlast files))]
            (upload files dest)))
        (doseq [command (:post deploy)]
          (ssh-exec command))))
    (println "no :deploy key in project.clj")))