(ns cake.task
  (:use cake
        [cake.server :only [print-stacktrace]]
        [cake.file :only [file newer? touch]]
        [cake.utils.useful :only [update verify append]]
        [uncle.core :only [*task-name*]]))

(declare tasks)
(declare run?)

(def implicit-tasks
  {'upgrade  ["Upgrade cake to the most current version."]
   'ps       ["List running cake jvm processes for all projects."]
   'log      ["Tail the cake log file. Optionally pass the number of lines of history to show."]
   'kill     ["Kill running cake jvm processes. Use -9 to force."]
   'killall  ["Kill all running cake jvm processes for all projects."]})

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

(defmacro append-task! [name task]
  `(do (defonce ~'task-defs (atom {}))
       (swap! ~'task-defs update '~name append '~task)))

(defn- expand-prefix
  "Converts a vector of the form [prefix sym1 sym1] to (prefix.sym1 prefix.sym2)"
  [ns]
  (if (sequential? ns)
    (map #(symbol (str (name (first ns)) "." (name %)))
         (rest ns))
    (list ns)))

(defmacro require-tasks!
  "Require all the specified namespaces and add them to the list of task namespaces."
  [namespaces]
  (let [namespaces (mapcat expand-prefix namespaces)]
    `(do (defonce ~'required-tasks (atom []))
         (apply require '~namespaces)
         (swap! ~'required-tasks into '~namespaces))))

(defn- resolve-var [ns sym]
  (when-let [ns (find-ns ns)]
    (when-let [var (ns-resolve ns sym)]
      @@var)))

(defn task-namespaces
  "Returns all required task namespaces for the given namespace (including transitive requirements)."
  [namespace]
  (try (require namespace)
       (catch java.io.FileNotFoundException e
         (when-not (= 'tasks namespace)
           (println "warning: unable to find tasks namespace" namespace)
           (println "  if you've added a new plugin to :dev-dependencies you must run 'cake deps' to install it"))))
  (into [namespace]
        (mapcat task-namespaces
                (resolve-var namespace 'required-tasks))))

(defn default-tasks []
  (if (= "global" (:artifact-id *project*))
    '[cake.tasks.global  tasks]
    '[cake.tasks.default tasks]))

(defn combine-task [task1 task2]
  (when-not (= {:replace true} task2)
    (let [task1 (or (if-not (:replace task2) task1)
                    {:actions [] :docs [] :deps #{}})]
      (append (apply dissoc task1 (:remove-deps task2))
              (select-keys task2 [:actions :docs :deps])))))

(defn get-tasks []
  (reduce
   (fn [tasks ns]
     (if-let [ns-tasks (resolve-var ns 'task-defs)]
       (merge-with combine-task tasks ns-tasks)
       tasks))
   {}
   (mapcat task-namespaces
           (into (default-tasks) (:tasks *project*)))))

(defn to-taskname [taskname]
  (symbol
   (name (ns-name *ns*))
   (if (string? taskname)
     (str "file-" (.replaceAll taskname "/" "-"))
     (str "task-" (name taskname)))))

(defn task-run-file [taskname]
  (file ".cake" "run" taskname))

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
  [taskname]
  (if-not (bound? #'tasks)
    ;; create the tasks and run? bindings if it hasn't been done yet.
    (binding [tasks (get-tasks)
              run?  {}]
      (run-task taskname))
    (let [task (get tasks taskname)]
      (if (and (nil? task)
               (not (string? taskname)))
        (println "unknown task:" taskname)
        (verify (not= :in-progress (run? taskname))
                (str "circular dependency found in task: " taskname)
                (when-not (run? taskname)
                  (set! run? (assoc run? taskname :in-progress))
                  (doseq [dep (:deps task)] (run-task dep))
                  (binding [*current-task* taskname
                            *task-name*    (name taskname)
                            *File* (if-not (symbol? taskname) (expand-defile-path taskname))]
                    (doseq [action (map resolve (:actions task)) :when action]
                      (action *opts*))
                    (set! run? (assoc run? taskname true))
                    (if (symbol? taskname)
                      (touch (task-run-file taskname) :verbose false)))))))))