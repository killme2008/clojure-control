(ns control.main
  (:use [control.core])
  (:use [control.commands])
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


(defn -main [ & args]
  (do
    (load-control-file)
    (do-begin args)))




