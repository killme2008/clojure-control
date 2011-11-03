(ns control.core
  (:use [clojure.java.io :only [reader]]
        [clojure.string :only [join blank?]]
        [clojure.walk :only [walk]]
		[clojure.contrib.def :only [defvar- defvar]]))

(def {^private: true} bash-reset "\033[0m")
(def {^private: true} bash-bold "\033[1m")
(def {^private: true} bash-redbold "\033[1;31m")
(def {^private: true} bash-greenbold "\033[1;32m")

(defmacro cli-bash-bold [& content]
  `(str bash-bold ~@content bash-reset))

(defmacro cli-bash-redbold [& content]
  `(str bash-redbold ~@content bash-reset))

(defmacro cli-bash-greenbold [& content]
  `(str bash-greenbold ~@content bash-reset))

(defvar- *runtime* (Runtime/getRuntime))

(defstruct ExecProcess :process :in :err :stdout :stderr :status)

(defn- spawn
  [cmdarray]
  (let [process (.exec *runtime* cmdarray)
		in (reader (.getInputStream process) :encoding "UTF-8")
		err (reader (.getErrorStream process) :encoding "UTF-8")
		execp (struct ExecProcess process in err)
		pagent (agent execp)]
	(send-off pagent (fn [exec-process] (assoc exec-process :stdout (str (:stdout exec-process) (join "\r\n" (doall (line-seq in)))))))
	(send-off pagent (fn [exec-process] (assoc exec-process :stderr (str (:stderr exec-process) (join "\r\n" (doall (line-seq err)))))))
	pagent))

(defn- await-process
  [pagent]
  (let [execp @pagent
		process (:process execp)
		in (:in execp)
		err (:err execp)]
	(await pagent)
	(.close in)
	(.close err)
	(.waitFor process)))
(defn gen-log
  [host tag content]
  (str (cli-bash-redbold host ":")  (cli-bash-greenbold tag ": ")  (join " " content)))

(defn log-with-tag
  [host tag & content]
  (if (not (blank? (join " " content)))
	(println (gen-log host tag content))))

(defn- not-nil?
  [obj]
  (not (nil? obj)))

(defn exec
  [host user cmdcol]
  (let [pagent (spawn (into-array String (filter not-nil? cmdcol)))
		status (await-process pagent)
		execp @pagent]
	(log-with-tag host "stdout" (:stdout execp))
	(log-with-tag host "stderr" (:stderr execp))
	(log-with-tag host "exit" status)
    (assoc execp :status status)))

(defn ssh-client
  [host user]
  (str user "@" host))

(defn- user-at-host?
  [host user]
  (fn [m]
	(and (= (:user m) user) (= (:host m) host))))

(defn- find-client-options
  [host user cluster sym]
  (let [m (first (filter (user-at-host? host user) (:clients cluster)))]
	(or (sym m) (sym cluster))))

(defn- make-cmd-array
  [cmd options others]
  (if (vector? options)
	(concat (cons cmd options) others)
	(cons cmd (cons options others))))

(defn ssh
  [host user cluster cmd]
  (let [ssh-options (find-client-options host user cluster :ssh-options)]
	(log-with-tag host "ssh" ssh-options cmd)
	(exec host user (make-cmd-array "ssh" ssh-options [(ssh-client host user) cmd]))))

(defn rsync
  [host user cluster src dst]
  (let [rsync-options (find-client-options host user cluster :rsync-options)]
	(log-with-tag host "rsync" rsync-options (str src " ==>" dst))
	(exec host user (make-cmd-array "rsync" rsync-options [src (str (ssh-client host user) ":" dst)]))))

(defn scp
  [host user cluster files remoteDir]
  (let [scp-options (find-client-options host user cluster :scp-options)]
	(log-with-tag host "scp" scp-options
	  (join " " (concat files [ " ==> " remoteDir])))
	(exec host user (make-cmd-array "scp" scp-options (concat files [(str (ssh-client host user) ":" remoteDir)])))))

(defvar tasks (atom (hash-map)))
(defvar clusters (atom (hash-map)))

(defmacro deftask
  [name desc arguments & body]
  (let [new-body (map #(concat (list (first %) 'host 'user 'cluster) (rest %)) body)]
	`(swap! tasks assoc ~name ~(list 'fn (vec (concat '[host user cluster] arguments)) (cons 'do new-body)))))

(defn- unquote-cluster [args]
  (walk (fn [item]
		  (cond (and (seq? item) (= `unquote (first item))) (second item)
				(or (seq? item) (symbol? item)) (list 'quote item)
				:else (unquote-cluster item)))
		identity
		args))

(defmacro defcluster
  [name & args]
  `(let [m# (apply hash-map ~(cons 'list (unquote-cluster args)))]
	 (swap! clusters assoc ~name (assoc m# :name ~name))))

(defmacro when-exit
  ([test error] `(when-exit ~test ~error nil))
  ([test error else]
	 `(if ~test
		(do (println ~error) (System/exit 1))
		~else)))

(defn- perform
  [host user cluster task taskName arguments]
  (do
	(println (cli-bash-bold "Performing " (name taskName) " for " host))
	(apply task host user cluster arguments)))

(defn- arg-count [f] (let [m (first (.getDeclaredMethods (class f))) p (.getParameterTypes m)] (alength p)))

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
				   task (taskName @tasks)]
			   (when-exit (nil? task) (str "No task named " (name taskName)))
			   (when-exit (and (empty? addresses)  (empty? clients)) (str "Empty clients for cluster " (name clusterName)))
			   (let [task-arg-count (- (arg-count task) 3)]
				 (when-exit (not= task-arg-count (count args)) (str "Task " (name taskName) " just needs " task-arg-count " arguments")))
			   (let [map-fn (if parallel pmap map)]
				 (println  (cli-bash-bold "Performing " (name clusterName) (if parallel " in parallel")))
				 (dorun (pmap #(perform % user cluster task taskName args) addresses))
				 (dorun (pmap #(perform (:host %) (:user %) cluster task taskName args) clients))
				 (shutdown-agents)))))


(defn begin
  []
  (do-begin *command-line-args*))
