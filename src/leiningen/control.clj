(ns leiningen.control
  (:use [control.core :only [do-begin clusters]]
           [leiningen.help :only [help-for]]
           [clojure.java.io :only [file]]))

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
  "Run user-defined clojure-control tasks against certain cluster"
  [project & args]
  (run-control project args))

(defn show
  "Show cluster info"
  [project & args]
  (do 
    (load-control-file project)
    (if-let [cluster-name (first args)] 
      (doseq
        [c (:clients ((keyword cluster-name) @clusters))]
        (println (str (:user c) "@" (:host c)))))))

(defn control
  "Leiningen plugin for Clojure-Control"
  {:help-arglists '([subtask [cluster task [args...]]])
   :subtasks [#'init #'run #'show]}
  ([project]
    (println (help-for "control")))
  ([project subtask & args]
    (case subtask
      "init" (apply init project args)
      "run" (apply run project args)
      "show" (apply show project args)
      (println (help-for "control")))))


