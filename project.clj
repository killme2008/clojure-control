(defproject control/control "0.4.2-SNAPSHOT" 
  :lein-release {:deploy-via :clojars}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [org.clojure/tools.cli "0.2.1"]]
  :author "dennis zhuang(killme2008@gmail.com)"
  :profiles {:dev {:dependencies [[codox "0.5.0"]]}}
  :url "https://github.com/killme2008/clojure-control"
  :main control.main
  :min-lein-version "2.0.0"
  :shell-wrapper {:bin "bin/clojure-control", :main control.main}
  :plugins [[lein-exec "0.1"]
            [lein-marginalia "0.7.0"]
            [lein-autodoc "0.9.0"]]
  :description "A clojure DSL for system admin and deployment with many remote machines")
