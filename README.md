## Introduction

Define clusters and tasks for system administration or code deployment, then execute them on one or many remote machines.

Clojure-control depends only on OpenSSH and clojure on the local control machine.Remote machines simply need a standard sshd daemon.

The idea came from [node-control](https://github.com/tsmith/node-control).

##Leiningen Usagew

To include clojure-control,add:

   		 [control "0.2.1"]

## Build

Clone this repository with git or download the latest version using the GitHub repository Downloads link.
Use [leiningen](https://github.com/technomancy/leiningen) to build project

		lein jar

And there will be a file named "control-0.2.1.jar" at project directory.Then use as a standard jar by adding it to your classpath.

Or you can just add src/control/core.clj to your classpath.

Type

		lein install

to install clojure-control,and it will add a shell script to ~/.lein/bin,then you can just use clojure-control to start your tasks:

       ~/.lein/bin/clojure-control control.clj mycluster task [args]

Note:if you use clojure-control shell script to start your tasks,please don't call (begin) in your control.clj,it will be called by the main function in shell script.

##Getting started

Get the current date from the two machines listed in the 'mycluster' config with a single command:

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

		;;start running
		(begin)

If saved in a file named "controls.clj",run with
   		
		java -cp clojure.jar:clojure-contrib.jar:control-0.2.1.jar clojure.main controls.clj mycluster date

If you have a script to start clojure,it can be started simply
   
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

## scp or rsync files

If you want to scp files to remote machines,you could use scp function
   
      (deftask :deploy "scp files to remote machines"
	        []
   	  		(scp ["release1.tar.gz" "release2.tar.gz"] "/home/alogin/"))

We defined a new task named "deploy" to copy release1.tar.gz and release2.tar.gz to remote machine's /home/alogin directory.

Also,you can use rsync to transfer files

		 (deftask :deploy "scp files to remote machines"
	        []
   	  		(rsync "src/" ":/home/login"))

Then it will be interpred as

	     rsync src/ user@host::/home/login
   
## Define clusters

As you seen the example in GettingStarted,you could use defcluster macro to define cluster making remote machines in a group.
If your machines have the same user,you can use defcluster macro more simply

   		(defcluster :mycluster
				 :user "login"
				 :addresses ["a.domain.com" "b.domain.com"])

Also,you can configure :clients for special machines:

		 (defcluster :mycluster
		 		  :clients [
				 		   { :host "c.domain.com" :user "clogin"}
				 		   ]
				  :user "login"	
				  :addresses ["a.domain.com" "b.domain.com"])

Then clojure-control will use "clogin" to login c.domain.com,but use "login" to login a.domain.com and b.domain.com.

Also,you can configure ssh,scp or rsync options for the whole cluster or special machines:

		 (defcluster :mycluster
		 	      :ssh-options "-p 44"
				  :scp-options "-v"
				  :rsync-options "-i"
		 		  :clients [
				 		   { :host "c.domain.com" :user "clogin" :ssh-options "-v -p 43"}
				 		   ]
				  :user "login"	
				  :addresses ["a.domain.com" "b.domain.com"])

Options can be a vector,for example:

			:rsync-options ["-vzrtopg","--delete"]

##Execute task in parallel

If you want to execute task for many remote machines in parallel,you can just configure cluster by

   	   (defcluster :mycluster
	              :parallel true
				  :user "login"
				  :addresses ["a.domain.com" "b.domain.com"])

Then every task run with mycluster will be in parallel for different hosts.				 


## Pass arguments to task

As you seen,define task using a argument vector to pass arguments for task.For example,i want to scp special file to remote machines,then

   	    (deftask :deploy "deploy a file to remote machine"
			  [file]
			  (scp [file] "/home/login")
			  (ssh (str "tar zxvf " file)))

Run with

	 	clojure controls.clj mycluster deploy release.tar.gz

Then "release.tar.gz" in command line arguments would be passed to scp macro as "file" argument,then copy it to remote machines and decompress it.

## Shell command DSL

Inspired by [Fabric](http://docs.fabfile.org/en/1.2.0/api/core/context_managers.html "fabric"), a set of shell context DSL are provided for your convenience.
You can use these macros in your ssh task.

Change directory then execute some command:

    (use [control.commands])
    (cd "/home/login"
        (run "ls"))

It would be executed as:

    cd /home/login; ls; 

cd could be combine to execute multiple commands:

    (cd "/home/login"
        (run "ls")
        (cd "bin"
            (run "ls")))

The result:

    cd /home/login; ls; cd bin; ls; 

Modify PATH:

    (cd "/home/login"
        (path "/home/login/bin"
            (run "clojure")))

The result:

    cd /home/login; export PATH=/home/login/bin:$PATH; clojure; 

Add environ variable for a command:

    (cd "/home/login"
        (path "/home/login/bin"
            (env "JAVA_OPTS" "-XMaxPermSize=128m"
                (run "clojure"))))
    
The result:

    cd /home/login; export PATH=/home/login/bin:$PATH; JAVA_OPTS=-XMaxPermSize=128m clojure; 

Ensure some prerequisite for some command:

    (prefix "source bin/activate"
        (run "pip install jip"))

The result:

    source bin/activate && pip install jip

Run command with sudo:

    (sudo "service tomcat start")

Will work as:

    sudo service tomcat start; 

## Leiningen Plugin

The lein-control plugin has been merged into clojure-control since
0.2.2. To use it within your project, just add clojure-control to your
dev-dependencies:

                :dev-dependency [[control "0.2.2"]]

and run `lein deps` to resolve it.

Now you can create an empty control file

                lein control init

By default, there will be a `control.clj` on your project home. You
can add clusters and tasks in the control's manner. To check your
cluster, use:

                lein control show <cluster-name>

When everything is ready, execute your tasks with :

                lein control run <cluster-name> <task-name> [args]

## Contributors

[sunng87](https://github.com/sunng87)  
[onycloud](https://github.com/onycloud/)






