## Introduction

Define clusters and tasks for system administration or code deployment, then execute them on one or many remote machines.

Clojure-control depends only on OpenSSH and clojure on the local control machine.Remote machines simply need a standard sshd daemon.

The idea came from [node-control](https://github.com/tsmith/node-control).

##News

 * Control 0.4.1 released.[ReleaseNotes](https://groups.google.com/forum/?fromgroups#!topic/clojure/MLR_5VfenSs)

## Installation

Clojure-Control bootstraps itself using the `control` shell script; there is no separate install script. It installs its dependencies upon the first run on unix, so the first run will take longer.

* [Download the script.](https://raw.github.com/killme2008/clojure-control/master/bin/control)
* Place it on your $PATH. (I like to use ~/bin)
* Set it to be executable. (`chmod 755 ~/bin/control`)

The link above will get you the stable release. 

On Windows most users can get the batch file. If you have wget.exe or curl.exe already installed and in PATH, you can just run `control self-install`, otherwise get the standalone jar from the downloads page. If you have Cygwin you should be able to use the shell script above rather than the batch file.

## Basic Usage

The [tutorial](https://github.com/killme2008/clojure-control/wiki/Getting-started) has a detailed walk-through of the steps involved in creating a control project, but here are the commonly-used tasks:

     control init                     #create a sample control file in current folder
	 control run CLUSTER TASK <args>  #run user-defined clojure-control tasks against certain cluster 
     control show CLUSTER             #show certain cluster info.

Use `control help` to see a complete list.

## Getting started

Creating a control file by:
    
	control init

It will create a file named `control.clj` under current folder.Defines your clusters and tasks in this file,for example:

```clj
     (defcluster :default-cluster
         :clients [
                  {:host "localhost" :user "root"}
         ])
     (deftask :date "echo date on cluster"  []
         (ssh "date"))
```

It defines a cluster named `default-cluster`,and defines a task named `date` to execute `date` command on remote machines.Run `date` task on `default-cluster` by:

    control run default-cluster date

Output:
```
    Performing default-cluster
    Performing date for localhost
    localhost:ssh:  date
    localhost:stdout: Sun Jul 24 19:14:09 CST 2011
    localhost:exit: 0
```
Also,you can run the task with `user@host` instead of a pre-defined cluster:
		 
		 control run root@localhost date

You may have to type password when running this task. You can setup ssh public keys to avoid typing a password when logining remote machines.please visit [HOWTO: set up ssh keys](http://pkeck.myweb.uga.edu/ssh/)

Every task's running result is a map contains output and status,you can get them by:

```clj
     (let [rt (ssh "date")]
       (println (:status rt))
       (println (:stdout rt))
       (println (:stderr rt)))
```


You can do whatever you want with these values,for example,checking status is right or writing standard output to a file.

##Some practical tasks

A task to ping mysql:

```clj

	(deftask :ping-mysql  []
	  (let [stdout (:stdout (ssh "mysqladmin -u root  -p'password' ping"))]
	      (if (.contains stdout "is alive")
      	  1
		  0)))
```

A task to deploy application:

```clj
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
```

Two tasks to install zookeeper c client:

```clj
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
```

##Documents

* [Getting started](https://github.com/killme2008/clojure-control/wiki/Getting-started)
* [Define clusters](https://github.com/killme2008/clojure-control/wiki/Define-clusters)
* [Define tasks](https://github.com/killme2008/clojure-control/wiki/Define-tasks)
* [DSL commands](https://github.com/killme2008/clojure-control/wiki/commands)
* [Clojure-Control shell commands](https://github.com/killme2008/clojure-control/wiki/Control-shell-commands)
* [API document](http://fnil.net/clojure-control/)


* [Wiki](https://github.com/killme2008/clojure-control/wiki)

## Contributors

[sunng87](https://github.com/sunng87)  

[onycloud](https://github.com/onycloud/) 

[ljos](https://github.com/ljos)

[dhilipsiva](https://github.com/dhilipsiva)

##License

MIT licensed




