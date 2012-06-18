(ns control.main
  (:use [control.core])
  (:use [clojure.string :only [join]]
        [clojure.tools.cli :only [cli]]
        [clojure.java.io :only [file reader writer]])
  (:gen-class))

(def ^{:private true :tag String} CONTROL-DIR "clojure-control.original.pwd")

(defn- get-control-dir []
  (System/getProperty CONTROL-DIR "."))

(defn- create-control-ns []
  (create-ns (gensym "user-control")))

(defn- load-control-file []
  (try 
    (binding [*ns* (create-control-ns)]
      (refer-clojure)
      (use '[control core commands])
      (load-file
       (str (get-control-dir) "/control.clj")))
    (catch java.io.FileNotFoundException e (error "control file not found."))))

(defn version
  "Print version for Clojure-control and the current JVM."
  []
  (println "Clojure-control" (System/getProperty "CONTROL_VER")
           "on Java" (System/getProperty "java.version")
           (System/getProperty "java.vm.name")))

(defn init
  "Initialize clojure-control, create a sample control file in current directory"
  [ & args]
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

(defn show
  "Show cluster info"
  [ & args]
  (do 
    (load-control-file)
    (if-let [cluster-name (first args)]
      (let [ cluster ((keyword cluster-name) @clusters)
            user (:user cluster)]
        (doseq
            [c (:clients cluster)]
          (println (ssh-client (:host c)  (or (:user c) user))))
        (doseq
            [a (:addresses cluster)]
          (println (ssh-client a user)))
        (doseq
            [c (:includes cluster)]
          (println (str "Cluster " c)))))))

(defn- run-control [ & args]
  (do
    (load-control-file)
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
  [ & args]
  (load-control-file)
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
    (println (format "Control server listen at %d" port))
    (await server)))

(defn run
  "Run user-defined clojure-control tasks against certain cluster,
      -r [--[no-]remote],running commands on remote control server,default is false.
      -p [--port] port, control server port,
      -h [--host] host, control server host."
  [  & args]
  (let [[options extra-args]
        (cli args
             ["-p" "--port" "Which port to connect." :default 8123 :parse-fn #(Integer/parseInt %)]
             ["-r" "--[no-]remote" :default false]
             [ "-h" "--host" "Which host to connect." :default "localhost"])
        {:keys [port host remote]} options]
    (if-not remote
      (apply run-control args)
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


(defn help-for [ name ]
  name)

(defn -main [ cmd & args]
  (case cmd
    "init" (apply init args)
    "run" (apply run args)
    "show" (apply show args)
    "server" (apply server args)
    "version" (apply version args)
    (println (help-for "control"))))




