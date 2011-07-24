;;
;;
;; A quick example for clojure-control
;;
;;
(ns samples
  (:use [control.core :only [task cluster scp ssh begin]]))
(cluster :mycluster
		 :clients [
				   { :host "a.domain.com" :user "alogin"}
				   { :host "b.domain.com" :user "blogin"}
				   ])

(task :date "Get date"
	  (ssh "date"))

(begin)
