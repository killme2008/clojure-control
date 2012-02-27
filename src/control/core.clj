(ns control.core
  (:use [clojure.java.io :only [reader]]
        [clojure.java.shell :only [sh]]
        [clojure.string :only [join blank?]]
        [clojure.walk :only [walk postwalk]]
        [clojure.contrib.def :only [defvar- defvar]]))

(def ^:dynamic *enable-color* true)
(def ^{:dynamic true} *enable-logging* true)
(defvar ^:dynamic  *debug* false)
(def ^:private bash-reset "\033[0m")
(def ^:private bash-bold "\033[1m")
(def ^:private bash-redbold "\033[1;31m")
(def ^:private bash-greenbold "\033[1;32m")

(defmacro cli-bash-bold [& content]
  `(if *enable-color*
     (str bash-bold ~@content bash-reset)
     (str ~@content)))

(defmacro cli-bash-redbold [& content]
  `(if *enable-color*
     (str bash-redbold ~@content bash-reset)
     (str ~@content)))

(defmacro cli-bash-greenbold [& content]
  `(if *enable-color*
     (str bash-greenbold ~@content bash-reset)
     (str ~@content)))


(defstruct ExecProcess  :stdout :stderr :status)

(defn gen-log [host tag content]
  (str (cli-bash-redbold host ":")
       (cli-bash-greenbold tag ": ")
       (join " " content)))

(defn log-with-tag [host tag & content]
  (if (and *enable-logging* (not (blank? (join " " content))))
    (println (gen-log host tag content))))

(defn- not-nil? [obj]
  (not (nil? obj)))

(defn  ^:dynamic  exec [host user cmdcol]
  (let [rt (apply sh (filter not-nil? cmdcol))
        status (:exit rt)
        stdout (:out rt)
        stderr (:err rt)
        execp (struct-map ExecProcess :stdout stdout :stderr stderr :status status)]
    (log-with-tag host "stdout" (:stdout execp))
    (log-with-tag host "stderr" (:stderr execp))
    (log-with-tag host "exit" status)
    execp))

(defn ssh-client [host user]
  (str user "@" host))

(defn- user-at-host? [host user]
  (fn [m]
    (and (= (:user m) user) (= (:host m) host))))

(defn- find-client-options [host user cluster sym]
  (let [m (first (filter (user-at-host? host user) (:clients cluster)))]
    (or (sym m) (sym cluster))))

(defn- make-cmd-array
  [cmd options others]
  (if (vector? options)
    (concat (cons cmd options) others)
    (cons cmd (cons options others))))

(defn ssh
  "Execute commands via ssh:
   (ssh \"date\")
   (ssh \"ps aux|grep java\")
"
  [host user cluster cmd & opts]
  (let [m (apply hash-map opts)
        sudo (:sudo m)
        cmd (if sudo
              (str "sudo " cmd)
              cmd)
        ssh-options (or (:ssh-options opts) (find-client-options host user cluster :ssh-options))]
	(log-with-tag host "ssh" ssh-options cmd)
	(exec host
          user
          (make-cmd-array "ssh"
                          ssh-options
                          [(ssh-client host user) cmd]))))

(defn rsync [host user cluster src dst]
  (let [rsync-options (find-client-options host user cluster :rsync-options)]
    (log-with-tag host "rsync" rsync-options (str src " ==>" dst))
    (exec host
          user
          (make-cmd-array "rsync"
                          rsync-options
                          [src (str (ssh-client host user) ":" dst)]))))

(def ^{:dynamic true} *tmp-dir* nil)

(defn scp
  "Copy local files to remote machines:
   (scp \"test.txt\" \"remote.txt\")
   (scp [\"1.txt\" \"2.txt\"] \"/home/deploy/\")
"
  [host user cluster local remote & opts]
  (let [files (if (coll? local)
                (vec local)
                [local])
        m (apply hash-map opts)
        scp-options (or (:scp-options m) (find-client-options host user cluster :scp-options))
        mode (:mode m)
        sudo (:sudo m)
        use-tmp (or sudo mode)
        tmp (if use-tmp
              (or *tmp-dir* (str "/tmp/control-" (System/currentTimeMillis) "/"))
              remote)]
    (log-with-tag host "scp" scp-options
      (join " " (concat files [ " ==> " tmp])))
    (if use-tmp
      (ssh host user cluster (str "mkdir -p " tmp)))
    (let [rt (exec host
                   user
                   (make-cmd-array "scp"
                                   scp-options
                                   (concat files [(str (ssh-client host user) ":" tmp)])))]
      (if mode
        (apply ssh host user cluster (str "chmod " mode  " " tmp "*") opts))
      (if use-tmp
        (apply ssh host user cluster (str "mv "  tmp "* " remote " ; rm -rf " tmp) opts)
        rt))))

;;All tasks defined in control file
(defvar tasks (atom (hash-map)))
;;All clusters defined in control file
(defvar clusters (atom (hash-map)))

(defvar- system-functions
  #{(symbol "scp") (symbol "ssh") (symbol "rsync") (symbol "call") (symbol "exists?")})

(defmacro
  ^{:doc "Define a task for executing on remote machines:
           (deftask :date \"Get date from remote machines\"
                     (ssh \"date\"))
"
    :arglists '([name doc-string? [params*] body])
    :added "0.1"}
  deftask [name & decl ]
  (let [m (if (string? (first decl))
            (next decl)
            decl)
        arguments (first m)
        body (next m)
        new-body (postwalk (fn [item]
                             (if (list? item)
                               (let [cmd (first item)]
                                 (if (cmd system-functions)
                                   (concat (list cmd  'host 'user 'cluster) (rest item))
                                   item))
                               item))
                           body)]
    (if (not (vector? arguments))
      (throw (IllegalArgumentException. "Task must have arguments even if []")))
    (if *debug*
      (prn new-body))
    `(swap! tasks
            assoc
            ~name
            ~(list 'fn
                   (vec (concat '[host user cluster] arguments))
                   (cons 'do new-body)))))

(defn call
  "Call other tasks in deftask,for example:
     (call :ps \"java\")"
  [host user cluster task & args]
  (apply
   (task @tasks)
   host user cluster args))

(defn exists?
  "Check if a file exists"
  [host user cluster file]
  (= (:status (ssh host user cluster (str "test -e " file))) 0))


(defn- unquote-cluster [args]
  (walk (fn [item]
          (cond (and (seq? item) (= `unquote (first item)))
                ,(second item)
                (or (seq? item) (symbol? item))
                ,(list 'quote item)
                :else
                ,(unquote-cluster item)))
        identity
        args))

(defmacro
  ^{:doc "Define a cluster including some remote machines"
    :arglists '([name & options])
    :added "0.1"}
  defcluster [name & args]
  `(let [m# (apply hash-map ~(cons 'list (unquote-cluster args)))]
     (swap! clusters assoc ~name (assoc m# :name ~name))))

(defmacro when-exit
  ([test error]
     `(when-exit ~test ~error nil))
  ([test error else]
     `(if ~test
        (do (println ~error) (throw (RuntimeException. ~error)))
        ~else)))

(defn- perform [host user cluster task taskName arguments]
  (do (if *enable-logging* (println (cli-bash-bold "Performing " (name taskName) " for " host)))
      (apply task host user cluster arguments)))

(defn- arg-count [f]
  (let [m (first (.getDeclaredMethods (class f)))
        p (.getParameterTypes m)]
    (alength p)))

(defn do-begin [args]
  (when-exit (< (count args) 2)
             "Please offer cluster and task name"
             (let [clusterName (keyword (first args))
                   taskName (keyword (second args))
                   args (next (next args))
                   cluster (clusterName @clusters)
                   parallel (:parallel cluster)
                   user (:user cluster)
                   addresses (:addresses cluster)
                   clients (:clients cluster)
                   task (taskName @tasks)
                   log (:log cluster)]
               (when-exit (nil? task)
                          (str "No task named " (name taskName)))
               (when-exit (and (empty? addresses)
                               (empty? clients))
                          (str "Empty clients for cluster "
                               (name clusterName)))
               (let [task-arg-count (- (arg-count task) 3)]
                 (when-exit (not= task-arg-count (count args))
                            (str "Task "
                                 (name taskName)
                                 " just needs "
                                 task-arg-count
                                 " arguments")))
               (binding [*enable-logging* (if (nil? log) true log)]
                 (if *enable-logging*
                   (println  (str bash-bold
                                  "Performing "
                                  (name clusterName)
                                  bash-reset
                                  (if parallel
                                    " in parallel"))))
                 (let [map-fn (if parallel pmap map)
                       a (doall (map-fn (fn [addr] [addr (perform addr user cluster task taskName args)])
                                        addresses))
                       c (doall (map-fn (fn [cli] [(:host cli) (perform (:host cli) (:user cli) cluster task taskName args)])
                                        clients))]
                   (into {} (concat a c)))))))

(defn begin []
  (do-begin *command-line-args*)
  (shutdown-agents))
