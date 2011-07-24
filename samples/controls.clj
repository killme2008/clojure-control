;;
;;
;; A quick example for clojure-control
;;
;;
(ns samples
  (:use [control.core :only [task cluster scp ssh begin]]))
;;define clusters
(cluster :mycluster
		 :clients [
				   { :host "a.domain.com" :user "alogin"}
				   { :host "b.domain.com" :user "blogin"}
				   ])

;;define tasks
(task :date "Get date"
	  (ssh "date"))

(task :deploy "scp files to remote machines"
	  (scp ("release.tar.gz") "/home/alogin/"))

;;start running
(begin)
