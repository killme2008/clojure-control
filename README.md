## Introduction

Define clusters and tasks for system administration or code deployment, then execute them on one or many remote machines.

Clojure-control depends only on OpenSSH and clojure on the local control machine.Remote machines simply need a standard sshd daemon.

The idea came from [node-control](https://github.com/tsmith/node-control).

## Gettting started
I recommend you to use clojure-control with lein control plugin,you can install the plugin by:

    lein plugin install control 0.3.8           #For clojure 1.3+
    lein plugin install control 0.3.1           #For clojure 1.2, some features are not valid in this version.Recommend using 0.3.3

And then use lein to create a control project,for example

    lein new mycontrol

Creating a control file by:

     cd mycontrol
     lein control init

It will create a file named `control.clj` under mycontrol folder.Defines your clusters and tasks in this file,for example:
    
     (defcluster :default-cluster
         :clients [
                  {:host "localhost" :user "root"}
         ])
     (deftask :date "echo date on cluster"  []
         (ssh "date"))

It defines a cluster named `default-cluster`,and defines a task named `date` to execute `date` command on remote machines.Run `date` task on `default-cluster` by:

    lein control run default-cluster date

Output:

    Performing default-cluster
    Performing date for localhost
    localhost:ssh:  date
    localhost:stdout: Sun Jul 24 19:14:09 CST 2011
    localhost:exit: 0

Also,you can run the task with `user@host` instead of a pre-defined cluster (since 0.3.5):
		 
		 lein control run root@localhost date

You may have to type password when running this task. You can setup ssh public keys to avoid typing a password when logining remote machines.please visit [HOWTO: set up ssh keys](http://pkeck.myweb.uga.edu/ssh/)

Every task's running result is a map contains output and status,you can get them by:

     (let [rt (ssh "date")]
       (println (:status rt))
       (println (:stdout rt))
       (println (:stderr rt)))

You can do whatever you want with these values,for example,checking status is right or writing standard output to a file.

##Some practical tasks

A task to ping mysql:

	(deftask :ping-mysql  []
	  (let [stdout (:stdout (ssh "mysqladmin -u root  -p'password' ping"))]
	      (if (.contains stdout "is alive")
      	  1
		  0)))

A task to deploy application:

    (deftask :deploy-app []
          (local "tar zcvf app.tar.gz app/")
          (scp "app.tar.gz" "/home/user/")
          (ssh
               (run 
                   (cd "/home/user"
    				   (run
	    			      (run "tar zxvf app.tar.gz")
       	    			  (env "JAVA_OPTS" "-XMaxPermSize=128m"
                             (run "bin/app.sh restart")))))))

Two tasks to install zookeeper c client:

     (deftask ldconfig
	   []
	     (ssh "ldconfig" :sudo true))

	 (deftask install_zk_client
	  []
	      (ssh
		   (run
		       (run "mkdir -p /home/deploy/dennis")
			   (cd "/home/deploy/dennis"
			           (run "wget http://labs.renren.com/apache-mirror//zookeeper/zookeeper-3.4.3/zookeeper-3.4.3.tar.gz"))))
	     (ssh (cd "/home/deploy/dennis"
	            (run "tar zxvf zookeeper-3.4.3.tar.gz")))
         (ssh (cd "/home/deploy/dennis/zookeeper-3.4.3/src/c"
		        (run
		              (run "./configure --includedir=/usr/include")
		              (run "make")
		              (run "sudo make install"))))
		  (call :ldconfig))

##Documents

* [Getting started](https://github.com/killme2008/clojure-control/wiki/Getting-started)
* [Define clusters](https://github.com/killme2008/clojure-control/wiki/Define-clusters)
* [Define tasks](https://github.com/killme2008/clojure-control/wiki/Define-tasks)
* [DSL commands](https://github.com/killme2008/clojure-control/wiki/commands)
* [Leiningen plugin commands](https://github.com/killme2008/clojure-control/wiki/Leiningen-plugin-commands)
* [API document](http://fnil.net/clojure-control/)


* [Wiki](https://github.com/killme2008/clojure-control/wiki)

## Contributors

[sunng87](https://github.com/sunng87)  

[onycloud](https://github.com/onycloud/) 

[ljos](https://github.com/ljos)

##License

MIT licensed




