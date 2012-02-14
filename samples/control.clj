;;
;;
;; A quick example for clojure-control
;;
;;
;;define clusters
(defcluster :mycluster
  :clients [
            { :host "a.domain.com" :user "alogin"}
            { :host "b.domain.com" :user "blogin"}
            ])

(define :all
  :ssh-options "-p 44"
  :scp-options "-v"
  :rsync-options "-i"
  :parallel true
  :user "deploy"
  :addresses ["a.domain.com" "b.domain.com"])

;;define tasks
(deftask :date "Get date"
  []
  (ssh "date"))

(deftask :build "Run build command on server"
  []
  (ssh (cd "/home/alogin/src"
           (path "/home/alogin/tools/bin/"
                 (env "JAVA_OPTS" "-XMaxPermSize=128m"
                      (run "./build.sh"))))))

(deftask :deploy "scp files to remote machines"
  [file1 file2]
  (scp [file1 file2] "/home/alogin/")
  (ssh (str "tar zxvf " file1))
  (ssh (str "tar zxvf " file2)))

