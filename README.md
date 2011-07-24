## Introduction

Define clusters and tasks for system administration or code deployment, then execute them on one or many remote machines.

Clojure-control depends only on OpenSSH and clojure on the local control machine.Remote machines simply need a standard sshd daemon.

The idea came from [node-control](https://github.com/tsmith/node-control).

##Getting started

Get the current date from the two machines listed in the 'mycluster' config with a single command:

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

If saved in a file named "controls.clj",run with
   		
		clojure controls.clj mycluster date

Each machine execute "date" command ,and the output form the remote machine is printed to the console.Exmaple console output

	 	Performing mycluster
		Performing date for a.domain.com
		a.domain.com:ssh: date
		a.domain.com:stdout: Sun Jul 24 19:14:09 CST 2011
		a.domain.com:exit: 0
		Performing date for b.domain.com
		b.domain.com:ssh: date
		b.domain.com:stdout: Sun Jul 24 19:14:09 CST 2011
		b.domain.com:exit: 0

Each line of output is labeled with the address of the machine the command was
executed on. The actual command sent and the user used to send it is
displayed. stdout and stderr output of the remote process is identified
as well as the final exit code of the local ssh command. 

## SCP files

If you want to scp files to remote machines,you could use scp function
   
      (task "deploy" "scp files to remote machines"
   	  		(scp "release.tar.gz" "/home/alogin/"))

We defined a new task named "deploy" to copy release.tar.gz to remote machine's /home/alogin directory.


   
## Define clusters

As you seen the example in GettingStarted,you could use cluster macro to define cluster making remote machines in a group.
If your machines have the same user,you can use cluster macro more simple

   		(cluster :mycluster
				 :user "login"
				 :addresses ["a.domain.com" "b.domain.com"])

Also,you can configure :clients for special machines:

		 (cluster :mycluster
		 		  :clients [
				 		   { :host "c.domain.com" :user "clogin"}
				 		   ]
				  :user "login"	
				  :addresses ["a.domain.com" "b.domain.com"])

Then clojure-control will use clogin with c.domain.com,but use login with a.domain.com and b.domain.com.






				





