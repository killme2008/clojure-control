(ns control.core
  #^{ :doc "Clojure control core"
     :author " Dennis Zhuang <killme2008@gmail.com>"}
  (:use [clojure.java.io :only [reader]]
        [clojure.java.shell :only [sh]]
        [clojure.string :only [join blank?]]
        [clojure.walk :only [walk postwalk]]))

(def ^:dynamic *enable-color* true)
(def ^{:dynamic true} *enable-logging* true)
(def ^:dynamic *debug* false)
(def ^:private bash-reset "\033[0m")
(def ^:private bash-bold "\033[1m")
(def ^:private bash-redbold "\033[1;31m")
(def ^:private bash-greenbold "\033[1;32m")
;;Global options for ssh,scp and rsync
(def ^{:dynamic true :private true} *global-options* (atom {}))

(defmacro ^:private cli-bash-bold [& content]
  `(if *enable-color*
     (str bash-bold ~@content bash-reset)
     (str ~@content)))

(defmacro ^:private cli-bash-redbold [& content]
  `(if *enable-color*
     (str bash-redbold ~@content bash-reset)
     (str ~@content)))

(defmacro ^:private cli-bash-greenbold [& content]
  `(if *enable-color*
     (str bash-greenbold ~@content bash-reset)
     (str ~@content)))


(defstruct ^:private ExecProcess  :stdout :stderr :status)

(defn gen-log [host tag content]
  (str (cli-bash-redbold host ":")
       (cli-bash-greenbold tag ": ")
       (join " " content)))

(defn-  log-with-tag [host tag & content]
  (if (and *enable-logging* (not (blank? (join " " content))))
    (println (gen-log host tag content))))

(defn  ^:dynamic  exec [host user cmdcol]
  (let [rt (apply sh (filter (complement nil?) cmdcol))
        status (:exit rt)
        stdout (:out rt)
        stderr (:err rt)
        execp (struct-map ExecProcess :stdout stdout :stderr stderr :status status)]
    (log-with-tag host "stdout" (:stdout execp))
    (log-with-tag host "stderr" (:stderr execp))
    (log-with-tag host "exit" status)
    execp))

(defn ssh-client [host user]
  (let [user (or user (:user @*global-options*))]
    (if (not user)
      (throw (IllegalArgumentException. "user is nil")))
    (str user "@" host)) )

(defn-  user-at-host? [host user]
  (fn [m]
    (and (= (:user m) user) (= (:host m) host))))

(defn- find-client-options [host user cluster opt]
  (let [opt (keyword opt)
        m (first (filter (user-at-host? host user) (:clients cluster)))]
    (or (opt m) (opt cluster) (opt @*global-options*))))

(defn- make-cmd-array
  [cmd options others]
  (if (vector? options)
    (concat (cons cmd options) others)
    (cons cmd (cons options others))))

(defn set-options!
  "Set global options for ssh,scp and rsync,
   key and value  could be:

      Key                               Value
  :ssh-options        a ssh options string,for example \"-o ConnectTimeout=3000\"
  :scp-options       a scp options string
  :rsync-options    a rsync options string.
  :user                    global user for cluster,if cluster do not have :user ,it will use this by default.

  Example:
        (set-options! :ssh-options \"-o ConnectTimeout=3000\")

  "
  ([key value]
     (swap! *global-options* assoc key value))
  ([key value & kvs]
     (set-options! key value)
     (if kvs
       (recur (first kvs) (second kvs) (next kvs)))))

(defn clear-options!
  "Clear global options"
  []
  (reset! *global-options* {}))

(defn ssh
  "Execute commands via ssh:
   (ssh \"date\")
   (ssh \"ps aux|grep java\")
   (ssh \"sudo apt-get update\" :sudo true)

   Valid options:
   :sudo   whether to run commands as root,default is false
   :ssh-options  -- ssh options string
"
  {:arglists '([cmd & opts])}
  [host user cluster cmd & opts]
  (let [m (apply hash-map opts)
        sudo (:sudo m)
        cmd (if sudo
              (str "sudo " cmd)
              cmd)
        ssh-options (or (:ssh-options m) (find-client-options host user cluster :ssh-options))]
	(log-with-tag host "ssh" ssh-options cmd)
	(exec host
          user
          (make-cmd-array "ssh"
                          ssh-options
                          [(ssh-client host user) cmd]))))

(defn rsync 
  "Rsync local files to remote machine's files,for example:
     (deftask :deploy \"scp files to remote machines\" []
    (rsync \"src/\" \":/home/login\"))

    Valid options:
    :rsync-options  -- rsync options string
  "
  {:arglists '([src dst & opts])}
  [host user cluster src dst & opts]
  (let [m (apply hash-map opts)
        rsync-options (or (:rsync-options m) (find-client-options host user cluster :rsync-options))]
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
   (scp [\"1.txt\" \"2.txt\"] \"/home/deploy/\" :sudo true :mode 755)

  Valid options:
    :sudo  -- whether to copy files to remote machines as root
    :mode -- files permission on remote machines
    :scp-options -- scp options string
"
  {:arglists '([local remote & opts])}
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
(defonce tasks (atom (hash-map)))
;;All clusters defined in control file
(defonce clusters (atom (hash-map)))

(def ^:private system-functions
  #{(symbol "scp") (symbol "ssh") (symbol "rsync") (symbol "call") (symbol "exists?")})

(defmacro
  ^{:doc "Define a task for executing on remote machines:
           (deftask :date \"Get date from remote machines\"
                     (ssh \"date\"))

          Please see https://github.com/killme2008/clojure-control/wiki/Define-tasks
"
    :arglists '([name doc-string? [params*] body])
    :added "0.1"}
  deftask [name & decl ]
  (let [name (keyword name)
        m (if (string? (first decl))
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
      (prn name "new-body:" new-body))
    `(swap! tasks
            assoc
            ~name
            ~(list 'fn
                   (vec (concat '[host user cluster] arguments))
                   (cons 'do new-body)))))

(defn call
  "Call other tasks in deftask,for example:
     (call :ps \"java\")"
  {:arglists '([task & args])}
  [host user cluster task & args]
  (apply
   (task @tasks)
   host user cluster args))

(defn exists?
  "Check if a file or directory is exists"
  {:arglists '([file])}
  [host user cluster file]
  (zero? (:status (ssh host user cluster (str "test -e " file)))))


(defn- unquote-cluster [args]
  (walk (fn [item]
          (cond (and (seq? item) (= `unquote (first item)))
                (second item)
                (or (seq? item) (symbol? item))
                (list 'quote item)
                :else
                (unquote-cluster item)))
        identity
        args))

(defmacro
  ^{:doc "Define a cluster including some remote machines,for example:
           (defcluster :mycluster
                     :user \"login\"
                     :addresses [\"a.domain.com\" \"b.domain.com\"])

      Please see https://github.com/killme2008/clojure-control/wiki/Define-clusters
     "
    :arglists '([name & options])
    :added "0.1"}
  defcluster [name & args]
  `(let [m# (apply hash-map ~(cons 'list (unquote-cluster args)))]
     (swap! clusters assoc ~name (assoc m# :name ~name))))

(defmacro ^:private when-exit
  ([test error]
     `(when-exit ~test ~error nil))
  ([test error else]
     `(if ~test
        (do (println ~error) (throw (RuntimeException. ~error)))
        ~else)))

(defn- perform [host user cluster task taskName arguments]
  (do (if *enable-logging* (println (cli-bash-bold "Performing " (name taskName) " for " (ssh-client host user))))
      (apply task host user cluster arguments)))

(defn- arg-count [f]
  (let [m (first (filter #(= (.getName %) "invoke") (.getDeclaredMethods (class f))))
        p (when m (.getParameterTypes m))]
    (if p
      (alength p)
      3)))

(defn do-begin [args]
  (when-exit (< (count args) 2)
             "Please offer cluster and task name"
             (let [cluster-name (keyword (first args))
                   task-name (keyword (second args))
                   task-args (next (next args))
                   cluster (cluster-name @clusters)
                   parallel (:parallel cluster)
                   user (:user cluster)
                   addresses (:addresses cluster)
                   clients (:clients cluster)
                   task (task-name @tasks)
                   includes (:includes cluster)
                   debug (:debug cluster)
                   log (:log cluster)]
               (when-exit (nil? task)
                          (str "No task named " (name task-name)))
               (when-exit (and (empty? addresses)
                               (empty? includes)
                               (empty? clients))
                          (str "Empty hosts for cluster "
                               (name cluster-name)))
               (let [task-arg-count (- (arg-count task) 3)]
                 (when-exit (> task-arg-count (count task-args))
                            (str "Task "
                                 (name task-name)
                                 " just needs "
                                 task-arg-count
                                 " arguments")))
               (binding [*enable-logging* (if (nil? log) true log)
                         *debug* debug]
                 (if *enable-logging*
                   (println  (str bash-bold
                                  "Performing "
                                  (name cluster-name)
                                  bash-reset
                                  (if parallel
                                    " in parallel"))))
                 (let [map-fn (if parallel pmap map)
                       a (doall (map-fn (fn [addr] [addr (perform addr user cluster task task-name task-args)])
                                        addresses))
                       c (doall (map-fn (fn [cli] [(:host cli) (perform (:host cli) (:user cli) cluster task task-name task-args)])
                                        clients))]
                   (merge (into {} (concat a c))
                          (when includes
                            (if (coll? includes)
                              (mapcat #(do-begin (cons % (next args))) includes)
                              (do-begin (cons (name includes) (next args)))))))))))

(defn begin []
  (do-begin *command-line-args*))
