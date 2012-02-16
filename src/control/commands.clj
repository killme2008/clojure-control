(ns #^{:doc "A set of DSL for ssh, inspired by Fabric"
       :author "Sun Ning <classicning@gmail.com>  Dennis Zhuang<killme2008@gmail.com>"}
  control.commands)


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
  `(str ~cmd " ; "))

(defn all
  "simply run several commands"
  [ & cmds]
  (let [rt  (apply str cmds)]
    (if (.endsWith rt " ; ")
      rt
      (str rt " ; "))))

(defmacro sudo
  "run a command with sudo"
  [cmd]
  `(if (.endsWith ~cmd " ; ")
     (str "sudo " ~cmd)
     (str "sudo " ~cmd " ; ")))

(defn append
  "Append a line to a file"
  [file line & opts]
  (let [m (apply hash-map opts)
        escaple (:escaple m)
        sudo (:sudo m)]
    (if sudo
      (str "echo '" line "' | sudo tee -a " file " ; ") 
      (str "echo '" line "' >> " file " ; "))))

(defn sed-
  [file before after flags backup limit]
  (str "sed -i" backup " -r -e \"" limit " s/"  before "/" after "/" flags "\" " file " ; "))

(defn sed
  "Use sed to replace strings matched pattern with options.Valid options include:
   :sudo   =>  true or false to use sudo,default is false.
   :flags   => sed options,default is nil.
   :limit    =>  sed limit,default is not limit.
   :backup  => backup file posfix,default is \".bak\"
   Equivalent to sed -i<backup> -r -e \"/<limit>/ s/<before>/<after>/<flags>g <filename>\"."

  [file before after & opts]
  (let [opts (apply hash-map opts)
        use-sudo (:sudo opts)
        flags (str (:flags opts) "g")
        backup (or (:backup opts) ".bak")
        limit (:limit opts)]
    (if use-sudo
      (sudo (sed- file before after flags backup limit))
      (sed- file before after flags backup limit))))

(defn  comm
  "Comments a line in a file with special character,default :char is \"#\"
   It use sed function to replace the line matched pattern, :sudo is also valid"
  [file pat & opts]
  (let [m (apply hash-map opts)
        char  (or (:char m) "#")]
    (apply sed file pat (str char "&") opts)))

(defn  uncomm
  "uncomment a line in a file"
  [file pat & opts]
  (let [m (apply hash-map opts)
        char  (or (:char m) "#")]
    (apply sed file (str char "(" pat ")") "\\1" opts)))

(defn cat
  "cat a file"
  [file]
  (str "cat " file))

(defn chmod
  "chmod [mod] [file]"
  [mod file]
  (str "chmod " mod " " file " ; "))

