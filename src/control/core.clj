(ns control.core
  (:use [clojure.java.io :only [reader]]
        [clojure.string :only [join blank?]]
        [clojure.walk :only [walk]]
		[clojure.contrib.def :only [defvar- defvar]]))


(defvar- *runtime* (Runtime/getRuntime))

(defstruct ExecProcess :process :in :err :stdout :stderr)
  
(defn- spawn
  [host cmdarray]
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

(defn- display
  [host tag content]
  (if (not (blank? (str content)))
	(println (str host ":" tag ": " content))))

(defn exec
  [host user cmdcol]
  (let [pagent (spawn host (into-array String cmdcol))
		status (await-process pagent)
		execp @pagent]
	(display host "stdout" (:stdout execp))
	(display host "stderr" (:stderr execp))
	(display host "exit" status)))

(defn client
  [host user]
  (str user "@" host))

(defn ssh
  [host user cmd]
  (display host "ssh" cmd)
  (exec host user ["ssh" (client host user) cmd])) 


(defmacro scp
  [host user files remoteDir]
  `(display ~host "scp" (str ~files " ==> " ~remoteDir))
  `(exec ~host ~user ["scp" ~@files (str (client ~host ~user) ":"  ~remoteDir)]))



(defvar tasks (transient (hash-map)))
(defvar clusters (transient (hash-map)))

(defmacro task
  [name desc & body]
  (let [new-body (map #(concat (list (first %) 'host 'user) (rest %)) body)]
	`(assoc! tasks ~name ~(list 'fn '[host user] (cons 'do new-body)))))

(defn- unquote-cluster [args]
  (walk (fn [item]
          (cond (and (seq? item) (= `unquote (first item))) (second item)
                (or (seq? item) (symbol? item)) (list 'quote item)
                :else (unquote-cluster item)))
        identity
        args))

(defmacro cluster
  [name & args]
  `(let [m# (apply hash-map ~(cons 'list (unquote-cluster args)))]
	 (assoc! clusters ~name (assoc m# :name name))))

(defmacro when-exit
  ([test error] `(when-exit ~test ~error nil))
  ([test error else]
	 `(if ~test
		(do (println ~error) (System/exit 1))
		~else)))

(defn- perform
  [host user task taskName]
  (do
	(println (str "Performing " (name taskName) " for " host))
	(task host user)))

(defn begin
  []
  (when-exit (or (nil? *command-line-args*) (< (count *command-line-args*) 2))
			 "Please offer cluster and task name"
			 (let [clusterName (keyword (first *command-line-args*))
				   taskName (keyword (second *command-line-args*))
				   cluster (clusterName clusters)
				   user (:user cluster)
				   addresses (:addresses cluster)
				   clients (:clients cluster)
				   task (taskName tasks)]
			   (when-exit (nil? task) (str "No task named " (name taskName)))
			   (when-exit (and (empty? addresses)  (empty? clients)) (str "Empty clients for cluster " (name clusterName)))
			   (do
				 (println  (str "Performing " (name clusterName)))
				 (dorun (map #(perform % user task taskName) addresses))
				 (dorun (map #(perform (:host %) (:user %) task taskName) clients))
				 (shutdown-agents)))))

