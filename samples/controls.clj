;;
;;
;; A quick example for clojure-control
;;
;;
(ns samples
  (:use [control.core :only [deftask defcluster scp ssh begin]]))
;;define clusters
(defcluster :mycluster
		 :clients [
				   { :host "a.domain.com" :user "alogin"}
				   { :host "b.domain.com" :user "blogin"}
				   ])

;;define tasks
(deftask :date "Get date"
	  []
	  (ssh "date"))

(deftask :deploy "scp files to remote machines"
	  [file1 file2]
	  (scp [file1 file2] "/home/alogin/")
	  (ssh (str "tar zxvf " file1))
	  (ssh (str "tar zxvf " file2)))

;;start running
(begin)
