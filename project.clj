(defproject control "0.3.7"
  :description "A clojure DSL for system admin and deployment with many remote machines"
  :url "https://github.com/killme2008/clojure-control"
  :author "dennis zhuang(killme2008@gmail.com)"
  :dependencies [[org.clojure/clojure "1.3.0"] [org.clojure/tools.cli "0.2.1"]]
  :lein-release {:deploy-via :clojars}
  :dev-dependencies [[lein-exec "0.1"]
                     [lein-marginalia "0.7.0"]
                     [lein-autodoc "0.9.0"]
                     [codox "0.5.0"]]
  :shell-wrapper {:bin "bin/clojure-control"
                  :main control.main})
