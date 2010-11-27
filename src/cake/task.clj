(ns cake.task
  (:use cake
        [cake.server :only [print-stacktrace]]
        [cake.file :only [file newer? touch]]
        [cake.utils.useful :only [update verify]]))

(defonce tasks (atom {}))
(def run? nil)

(def implicit-tasks
  {'reload   ["Reload any .clj files that have changed or restart."]
   'upgrade  ["Upgrade cake to the most current version."]
   'ps       ["List running cake jvm processes for all projects."]
   'log      ["Tail the cake log file. Optionally pass the number of lines of history to show."]
   'kill     ["Kill running cake jvm processes. Use -9 to force."]
   'killall  ["Kill all running cake jvm processes for all projects."]})

(defn load-tasks [tasks]
  (let [complain? (seq (.listFiles (file "lib")))]
    (doseq [ns tasks]
      (try (require ns)
           (catch Exception e
             (when complain? (print-stacktrace e)))))))

(defn parse-task-opts [forms]
  (let [[deps forms] (if (set? (first forms))
                      [(first forms) (rest forms)]
                      [#{} forms])
        deps (map #(if-not (symbol? %) (eval %) %) deps)
        [docs forms] (split-with string? forms)
        [destruct forms] (if (map? (first forms))
                           [(first forms) (rest forms)]
                           [{} forms])
        [pred forms] (if (= :when (first forms))
                       `[~(second forms) ~(drop 2 forms)]
                       [true forms])]
    {:deps deps :docs docs :actions forms :destruct destruct :pred pred}))

(defn append-task! [task-name task-var]
  (swap! tasks update task-name
         (fn [task]
           (let [task (or task ())]
             (if (some (partial = task-var) task)
               task
               (conj task task-var))))))

(defn get-task [name]
  (reduce
   (fn [task {:keys [remove-dep action deps docs]}]
     (-> task
         (update :actions conj action)
         (update :deps    into deps)
         (update :deps    disj remove-dep)
         (update :docs    into docs)))
   {:actions [] :deps #{} :docs []}
   (reverse
    (take-while (complement nil?) (get @tasks name)))))

(defn to-taskname [taskname]
  (symbol
   (name (ns-name *ns*))
   (if (string? taskname)
     (str "file-" (.replaceAll taskname "/" "-"))
     (str "task-" (name taskname)))))

(defn task-run-file [task-name]
  (file ".cake" "run" task-name))

(defn run-file-task? [target-file deps]
  (let [{file-deps true task-deps false} (group-by string? deps)]
    (or (not (.exists target-file))
        (some #(newer? % target-file)
              (into file-deps
                    (map #(task-run-file %)
                         task-deps)))
        (empty? deps))))

(defn- expand-defile-path [path]
  (file (.replaceAll path "\\+context\\+" (str (:context *project*)))))

(defn run-task
  "Execute the specified task after executing all prerequisite tasks."
  [name]
  (let [task (get-task name)]
    (if (and (nil? task)
             (not (string? name)))
      (println "unknown task:" name)
      (verify (not= :in-progress (run? name))
              (str "circular dependency found in task: " name)
        (when-not (run? name)
          (set! run? (assoc run? name :in-progress))
          (doseq [dep (:deps task)] (run-task dep))
          (binding [*current-task* name
                    *File* (if-not (symbol? name) (expand-defile-path name))]
            (doseq [action (map resolve (:actions task)) :when action]              
              (action *opts*))
            (set! run? (assoc run? name true))
            (if (symbol? name)
              (touch (task-run-file name) :verbose false))))))))