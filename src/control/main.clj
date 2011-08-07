(ns control.main
  (:use [control.core])
  (:use [control.commands]))

(defn- load-control-file [filename]
  (try 
    (binding [*ns* (the-ns 'control.main)]
      (load-file filename))
  (catch java.io.FileNotFoundException e (println "control file not found."))))

(defn -main
  [file & args]
  (do
    (load-control-file file)
    (do-begin args)))

