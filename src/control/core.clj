(ns control.core
  (:use [clojure.java.io :only [reader]]
        [clojure.string :only [join blank?]]
        [clojure.walk :only [walk]]
		[clojure.contrib.def :only [defvar- defvar]]))


(defvar- *runtime* (Runtime/getRuntime))

(defstruct ExecProcess :process :in :err :stdout :stderr)

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
  (str host ":" tag ": " content))

(defn log-with-tag
  [host tag content]
  (if (not (blank? (str content)))
	(println (gen-log host tag content))))

(defn exec
  [host user cmdcol]
  (let [pagent (spawn (into-array String cmdcol))
		status (await-process pagent)
		execp @pagent]
	(log-with-tag host "stdout" (:stdout execp))
	(log-with-tag host "stderr" (:stderr execp))
	(log-with-tag host "exit" status)))

(defn ssh-client
  [host user]
  (str user "@" host))

(defn ssh
  [host user cmd]
  (log-with-tag host "ssh" cmd)
  (exec host user ["ssh" (ssh-client host user) cmd]))


(defmacro scp
  [host user files remoteDir]
  `(do (log-with-tag ~host "scp"
         (join " " (concat ~files [ " ==> " ~remoteDir])))
       (exec ~host ~user ["scp" ~@files (str (ssh-client ~host ~user) ":"
                                             ~remoteDir)])))



(defvar tasks (atom (hash-map)))
(defvar clusters (atom (hash-map)))

(defmacro deftask
  [name desc arguments & body]
  (let [new-body (map #(concat (list (first %) 'host 'user) (rest %)) body)]
	`(swap! tasks assoc ~name ~(list 'fn (vec (concat '[host user] arguments)) (cons 'do new-body)))))

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
  [host user task taskName arguments]
  (do
	(println (str "Performing " (name taskName) " for " host))
	(apply task host user arguments)))

(defn- arg-count [f] (let [m (first (.getDeclaredMethods (class f))) p (.getParameterTypes m)] (alength p)))

(defn do-begin [args]
  (let [clusterName (keyword (first args))
		taskName (keyword (second args))
		args (next (next args))
		cluster (clusterName @clusters)
		user (:user cluster)
		addresses (:addresses cluster)
		clients (:clients cluster)
		task (taskName @tasks)]
	(when-exit (nil? task) (str "No task named " (name taskName)))
	(when-exit (and (empty? addresses)  (empty? clients)) (str "Empty clients for cluster " (name clusterName)))
	(let [expect-count (- (arg-count task) 2)]
      (when-exit (not= expect-count (count args)) (str "Task " (name taskName) " just needs " expect-count " arguments")))
	(do
	  (println  (str "Performing " (name clusterName)))
	  (dorun (map #(perform % user task taskName args) addresses))
	  (dorun (map #(perform (:host %) (:user %) task taskName args) clients))
	  (shutdown-agents))))


(defn begin
  []
  (when-exit (or (nil? *command-line-args*) (< (count *command-line-args*) 2))
			 "Please offer cluster and task name"
			 (do-begin *command-line-args*)))
