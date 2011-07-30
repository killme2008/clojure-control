(ns control.test.commands
  (:use [control.core])
  (:use [control.commands])
  (:use [clojure.test]))

(deftest test-cd 
  (is (= "cd /home/sun; " (cd "/home/sun"))))

(deftest test-path
  (is (= "export PATH=/home/sun/bin:$PATH; " (path "/home/sun/bin"))))

(deftest test-env-run
  (is (= "JAVA_OPTS=-XMaxPermSize=128m java -version; "
        (env "JAVA_OPTS" "-XMaxPermSize=128m"
          (run "java -version")))))

(deftest test-cd-run
  (is (= "cd /home/sun; ls; "
        (cd "/home/sun"
          (run "ls")))))

(deftest test-cd-prefix-run
  (is (= "cd /home/sun; source ~/.bash_profile && ls; "
        (cd "/home/sun"
          (prefix "source ~/.bash_profile"
            (run "ls"))))))

(deftest test-sudo
  (is (= "sudo service tomcat start; "
        (sudo "service tomcat start"))))