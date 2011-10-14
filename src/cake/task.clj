(ns cake.task
  (:use cake
        [bake.core :only [print-stacktrace log verbose?]]
        [cake.file :only [file newer? touch]]
        [cake.utils.version :only [version-mismatch?]]
        [cake.project :only [add-group]]
        [useful.utils :only [verify adjoin]]
        [useful.map :only [update filter-vals]]
        [uncle.core :only [*task-name*]]
        [clojure.set :only [difference]]
        [clojure.string :only [split]]
        [clojure.java.io :only [writer]]
        [clojure.contrib.prxml :only [*prxml-indent* prxml]]))

(declare tasks)
(declare run?)

(def implicit-tasks
  {'upgrade  ["Upgrade cake to the most current version."]
   'ps       ["List running cake jvm processes for all projects."]
   'log      ["Tail the cake log file. Optionally pass the number of lines of history to show."]
   'kill     ["Kill running cake jvm processes. Use -9 to force."]
   'killall  ["Kill all running cake jvm processes for all projects."]
   'console  ["Open jconsole on your project. Optionally pass the number of tiled windows."]
   'pid      ["Print the pid of the cake jvm running in the current directory."]
   'port     ["Print the port of the cake jvm running in the current directory."]})

(defn parse-task-opts [forms]
  (let [[deps forms] (if (set? (first forms))
                      [(first forms) (rest forms)]
                      [#{} forms])
        deps (set (map #(if-not (symbol? %) (eval %) %) deps))
        [docs forms] (split-with string? forms)
        [destruct forms] (if (map? (first forms))
                           [(first forms) (rest forms)]
                           [{} forms])
        [pred forms] (if (= :when (first forms))
                       `[~(second forms) ~(drop 2 forms)]
                       [true forms])]
    {:deps (list `quote deps) :docs (vec docs) :actions forms :destruct destruct :pred pred}))

(defn append-task! [name task]
  (let [tasks-var (or (resolve 'task-defs)
                      (intern *ns* 'task-defs (atom {})))]
    (swap! @tasks-var update name adjoin task)))

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
  (when namespace
    (try (require namespace)
         (catch java.io.FileNotFoundException e
           (when (and (not= 'tasks namespace)
                      (not= "deps" (first *args*)))
             (println "warning: unable to find tasks namespace" namespace)
             (println "  if you've added a new plugin to :dev-dependencies you must run 'cake deps' to install it"))))
    (into [namespace]
          (mapcat task-namespaces
                  (resolve-var namespace 'required-tasks)))))

(defn default-tasks []
  (if (= "global" (:artifact-id *project*))
    '[cake.tasks.global  tasks]
    '[cake.tasks.default tasks]))

(defn combine-task [task1 task2]
  (when-not (= {:replace true} task2)
    (let [task1 (or (when-not (:replace task2) task1)
                    {:actions [] :docs [] :deps #{} :bake-deps []})]
      (adjoin (update task1 :deps difference (:remove-deps task2))
              (select-keys task2 [:actions :docs :deps])))))

(defn plugin-namespace [dep]
  (let [plugin-name (-> dep first name)]
    (when-let [ns (second (re-matches #"^cake-(.*)$" plugin-name))]
      (symbol (str "cake.tasks." ns)))))

(defn get-tasks []
  (reduce
   (fn [tasks ns]
     (if-let [ns-tasks (resolve-var ns 'task-defs)]
       (merge-with combine-task tasks ns-tasks)
       tasks))
   {}
   (mapcat task-namespaces
           (concat (default-tasks)
                   (map plugin-namespace
                        (filter-vals (:dependencies *project*) :plugin))
                   (:tasks *project*)))))

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

(defmulti generate-file
  (fn [forms]
    (let [parts (-> *File* (.toString) (.split "\\."))]
      (when (< 1 (count parts))
        (-> parts last keyword)))))

(defmacro with-outfile [& forms]
  `(with-open [f# (writer *File*)]
     (binding [*out* f#]
       ~@forms)))

(defmethod generate-file nil
  [forms]
  (with-outfile
    (doseq [form forms]
      (println form))))

(defmethod generate-file :clj
  [forms]
  (with-outfile
    (doseq [form forms]
      (prn form)
      (println))))

(defmethod generate-file :xml
  [forms]
  (with-outfile
    (binding [*prxml-indent* 2]
      (prxml forms))
    (println)))

(defn- run-actions
  "Execute task actions in order. Construct file output if task is a defile"
  [task]
  (let [results (doall (for [action (:actions task) :when action]
                         (action *opts*)))]
    (when *File*
      (when-let [output (seq (remove nil? results))]
        (log "generating file")
        (generate-file output)))))

(defmacro without-circular-deps [taskname & forms]
  `(do (verify (not= :in-progress (run? ~taskname))
               (str "circular dependency found in task: " ~taskname))
       (when-not (run? ~taskname)
         (set! run? (assoc run? ~taskname :in-progress))
         ~@forms
         (set! run? (assoc run? ~taskname true)))))

(defn check-bake-deps [task]
  (doseq [[project version] (:bake-deps task)]
    (let [actual (get-in *project* [:dependencies (add-group project) :version])]
      (when (version-mismatch? version actual)
        (log (str (format "Warning: cannot find required dependency %s %s" project version)
                  (when actual (str "; found " actual))))))))

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
        (without-circular-deps taskname
          (doseq [dep (:deps task)] (run-task dep)) ;; run dependencies
          (binding [*current-task* taskname
                    *task-name*    (name taskname)
                    *File* (if-not (symbol? taskname) (expand-defile-path taskname))]
            (when (verbose?)
              (log "Starting..."))
            (check-bake-deps task)
            (run-actions task))
          (when (symbol? taskname)
            (touch (task-run-file taskname))))))))
