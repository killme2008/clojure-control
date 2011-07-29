(ns #^{:doc "A set of DSL for ssh, inspired by Fabric"
       :author "Sun Ning <classicning@gmail.com>"}
  control.commands
  (:use [clojure.contrib.string :only [join]]))

(defmacro path
  "modify shell path"
  [new-path & cmd]
  `(str "export PATH=" ~new-path ":$PATH; " ~@cmd))

(defmacro cd 
  "change current directory"
  [path & cmd]
  `(str "cd " ~path "; " ~@cmd))

(defmacro prefix 
  "execute a prefix command, for instance, activate shell profile"
  [pcmd & cmd]  
  `(str ~pcmd " && " ~@cmd))

(defmacro env 
  "declare a env variable for next command"
  [key val & cmd]
  `(str ~key "=" ~val " " ~@cmd))

(defmacro run
  "simply run a command"
  [cmd]
  `(str ~cmd "; "))
