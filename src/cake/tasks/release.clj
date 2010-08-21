(ns cake.tasks.release
  (:use cake cake.core
        [cake.tasks.jar :only [jarfile]]
        [clojure.java.io :only [copy]]
        [useful :only [verify]]
        [cake.ant :only [log]])
  (:import [com.jcraft.jsch JSch ChannelExec Logger UserInfo JSchException]
           [java.io FileInputStream]))

(def *session* nil)

(defn keyfiles []
  (if-let [id (or (:identity *opts*) (:i *opts*))]
    [(file id)]
    (for [id ["id_rsa" "id_dsa" "identity"] :let [keyfile (file "~/.ssh" id)] :when (.exists keyfile)]
      keyfile)))

(defn- session-connect [{:keys [host port username password passphrase keyfile]}]
  (let [jsch    (JSch.)]
    (JSch/setLogger
     (proxy [Object Logger] []
       (isEnabled [level] (debug?))
       (log [level message] (println message))))
    (when keyfile (.addIdentity jsch (.getPath keyfile)))
    (try (doto (.getSession jsch username host (or port 22))
           (.setUserInfo
            (proxy [Object UserInfo] []
              (getPassword      []  password)
              (getPassphrase    []  passphrase)
              (promptYesNo      [_] true)
              (promptPassword   [_] true)
              (promptPassphrase [_] true)
              (showMessage      [_])))
           (.connect))
         (catch JSchException e
           (case (.getMessage e)
             "Auth cancel" nil
             "Auth fail" false)))))

(defn- prompt-passphrase [keyfile]
  (prompt-read (format "Enter passphrase for key '%s'" (.getPath keyfile)) :echo false))

(defn- prompt-password []
  (prompt-read "Password:" :echo false))

(defn start-session [{:keys [host port username] :as opts}]
  (or (first (remove nil?
        (for [keyfile (keyfiles)]
          (let [opts (assoc opts :keyfile keyfile)
                session (session-connect opts)]
            (if (false? session) nil
                (or session
                    (session-connect (assoc opts :passphrase (prompt-passphrase keyfile)))))))))
      (session-connect :password (prompt-password))))

(defmacro ssh-session [opts & forms]
  `(binding [*session* (start-session ~opts)]
     (try ~@forms
          (finally (.disconnect *session*)))))

(defn- wait-for-ack [in]
  (let [code (.read in)]
    (verify (= 0 code) "error")))

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
        (ssh-exec (str "scp -r -d -t " dest)
          (fn [in out ext]
            (doseq [f src :let [f (file f)]]
              (when (verbose?) (log "uploading:" (.getAbsolutePath f)))
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

(defn upload-to-clojars [jar]
  (log "Uploading" jar "to clojars")
  (ssh-session {:host "clojars.org" :username "clojars"}
    (upload ["pom.xml" (jarfile)])))

(deftask release #{jar}
  "Release project jar to clojars."
  (upload-to-clojars (jarfile)))
