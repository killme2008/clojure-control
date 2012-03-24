(ns leiningen.control
  #^{ :doc "Clojure control leiningen plugin"
         :author "Sun Ning <classicning@gmail.com>  Dennis Zhuang <killme2008@gmail.com>"}
  (:use [control.core :only [do-begin clusters]]
        [clojure.string :only [join]]
        [clojure.tools.cli :only [cli]]
        [leiningen.help :only [help-for]]
        [clojure.java.io :only [file reader writer]]))

(defn- get-config [project key]
  (get-in project [:control key]))

(defn- create-control-ns []
  (create-ns (gensym "user-control")))

(defn- load-control-file [project]
  (try 
    (binding [*ns* (create-control-ns)]
      (refer-clojure)
      (use '[control core commands])
      (load-file
       (or 
        (get-config project :control-file)
        "./control.clj")))
    (catch java.io.FileNotFoundException e (println "control file not found."))))

(defn- run-control [project args]
  (do
    (load-control-file project)
    (do-begin args)))

(defn- handle-conn
  [^java.net.Socket socket]
  (with-open [s socket
              rdr (reader socket)
              wtr (writer socket)]
    (try
      (let [line (.readLine rdr)
            args (seq (.split line " "))
            rt (with-out-str 
                 (do-begin (vec args)))]
        (spit wtr rt))
      (catch Throwable e
        (spit wtr (.getMessage e))))))

(defn server
  "Start a control server for handling requests:
      -p [--port]  port , listen on which port"
  [project & args]
  (load-control-file project)
  (let [[options _ banner]
        (cli args
             ["-p" "--port" "Which port to listen on." :default 8123
              :parse-fn #(Integer/parseInt %)])
        {:keys [port]} options
        ^java.net.ServerSocket ss (java.net.ServerSocket. port)
        server (agent ss)]
    (set-error-handler! server
                        (fn [_ e]
                          (.printStackTrace e)))
    (send-off server
              (fn [^java.net.ServerSocket ss]
                (let [s (.accept ss)]
                  (handle-conn s)
                  (recur ss))))
    (await server)))

(defn init
  "Initialize clojure-control, create a sample control file in project home"
  [project & args]
  (let [control-file (file "." "control.clj")]
    (if-not (.exists control-file)
      (spit control-file 
            (str 
             "(defcluster :default-cluster\n"
             "  :clients [\n"
             "    {:host \"localhost\" :user \"root\"}\n"
             "  ])\n"
             "\n"
             "(deftask :date \"echo date on cluster\""
             "  []\n"
             "  (ssh \"date\"))\n")))))

(defn run
  "Run user-defined clojure-control tasks against certain cluster,
      -r [--[no-]remote],running commands on remote control server,default is false.
      -p [--port] port, control server port,
      -h [--host] host, control server host."
  [project & args]
  (let [[options extra-args]
        (cli args
             ["-p" "--port" "Which port to connect." :default 8123 :parse-fn #(Integer/parseInt %)]
             ["-r" "--[no-]remote" :default false]
             [ "-h" "--host" "Which host to connect." :default "localhost"])
        {:keys [port host remote]} options]
    (if-not remote
      (run-control project args)
      (let [^java.net.Socket sock (java.net.Socket. host port)]
        (.setTcpNoDelay sock true)
        (let [rdr (reader sock)
              wtr (writer sock)]
          (.write ^java.io.Writer wtr (str (join " " extra-args) "\n"))
          (.flush wtr)
          (println (slurp rdr))
          (.close wtr)
          (.close rdr)
          (.close sock))))))

(defn show
  "Show cluster info"
  [project & args]
  (do 
    (load-control-file project)
    (if-let [cluster-name (first args)]
      (let [user (:user ((keyword cluster-name) @clusters))]
        (doseq
            [c (:clients ((keyword cluster-name) @clusters))]
          (println (str (:user c) "@" (:host c))))
        (doseq
            [a (:addresses ((keyword cluster-name) @clusters))]
          (println (str user "@" a)))))))


(defn control
  "Leiningen plugin for Clojure-Control"
  {:help-arglists '([subtask [cluster task [args...]]])
   :subtasks [#'init #'run #'show #'server]}
  ([project]
     (println (help-for "control")))
  ([project subtask & args]
     (case subtask
           "init" (apply init project args)
           "run" (apply run project args)
           "show" (apply show project args)
           "server" (apply server project args)
           (println (help-for "control")))))


