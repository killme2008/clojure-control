## Introduction

Define clusters and tasks for system administration or code deployment, then execute them on one or many remote machines.

Clojure-control depends only on OpenSSH and clojure on the local control machine.Remote machines simply need a standard sshd daemon.

The idea came from [node-control](https://github.com/tsmith/node-control).

## Gettting started
I recommend you to use clojure-control with lein control plugin,you can install the plugin by:

    lein plugin install control 0.3.2           #For clojure 1.3
    lein plugin install control 0.3.1           #For clojure 1.2

And then use lein to create a control project,for example

    lein new mycontrol

Then edit `mycontrol/project.clj` to add control dependency:

     [control "0.3.2"]  ;clojure 1.3
     [control "0.3.1"]  ;clojure 1.2

Type `lein deps` to resolve dependencies.Creating a control file by:

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

You may have to type password when running this task. You can setup ssh public keys to avoid typing a password when logining remote machines.please visit [HOWTO: set up ssh keys](http://pkeck.myweb.uga.edu/ssh/)

##Documents

Please visit [wiki pages](https://github.com/killme2008/clojure-control/wiki).

## Contributors

[sunng87](https://github.com/sunng87)  

[onycloud](https://github.com/onycloud/) 

[ljos](https://github.com/ljos)






