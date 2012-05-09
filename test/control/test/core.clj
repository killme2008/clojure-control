(ns control.test.core
  (:use [control.core])
  (:use [clojure.test])
  (:use [clojure.string :only [blank?]]))


(defn control-fixture [f]
  (try
	(f)
	(finally
	 (reset! tasks (hash-map))
	 (reset! clusters (hash-map)))))

(use-fixtures :each control-fixture)

(defn- arg-count [f] (let [m (first (.getDeclaredMethods (class f))) p (.getParameterTypes m)] (alength p)))
(deftest test-gen-log
  (binding [*enable-color* false]
    (is (= "localhost:ssh: test" (gen-log "localhost" "ssh" '("test"))))
    (is (= "a.domain.com:scp: hello world" (gen-log "a.domain.com" "scp" '("hello world"))))))

(deftest test-ssh-client
  (is (= "apple@localhost" (ssh-client "localhost" "apple")))
  (is (= "dennis@a.domain.com" (ssh-client "a.domain.com" "dennis"))))

(deftest test-local
  (is (= "1\n" (:stdout (local "echo 1"))))
  )

(deftest test-task
  (is (= 0 (count @tasks)))
  (deftask :test "test task"
	[]
	(+ 1 2))
  (is (= 1 (count @tasks)))
  (is (= 0 (count @clusters)))
  (is (function? (:test @tasks)))
  (is (= 3 (arg-count (:test @tasks))))
  (is (= 3 ((:test @tasks) 3 4 5))))


(deftest test-cluster
  (is (= 0 (count @clusters)))
  (defcluster :mycluster
	:clients [{:host "a.domain.com" :user "apple"}]
	:addresses ["a.com" "b.com"]
	:user "dennis"
	)
  (is (= 0 (count @tasks)))
  (is (= 1 (count @clusters)))
  (let [m (:mycluster @clusters)]
	(is (= :mycluster (:name m)))
	(is (= [{:host "a.domain.com" :user "apple"}] (:clients m)))
	(is (= "dennis" (:user m)))
	(is (= ["a.com" "b.com"] (:addresses m)))))


(defmacro with-private-fns [[ns fns] & tests]
  "Refers private fns from ns and runs tests in context."
  `(let ~(reduce #(conj %1 %2 `(ns-resolve '~ns '~%2)) [] fns)
	 ~@tests))

(with-private-fns [control.core [perform spawn await-process user-at-host? find-client-options make-cmd-array *global-options* create-clients]]
  (deftest test-make-cmd-array
	(is (= ["ssh" "-v" "-p 44" "user@host"] (make-cmd-array "ssh" ["-v" "-p 44"] ["user@host"])))
	(is (= ["rsync" "-arz" "--delete" "src" "user@host::share"] (make-cmd-array "rsync" ["-arz" "--delete"] ["src" "user@host::share"]))))
  (deftest test-create-clients
    (is (= [{:user "deploy" :host "host"}] (create-clients "deploy@host"))))
  (deftest test-perform
	(deftask :test "test-task"
	  [a b]
	  (+ a b))
	(let [t (:test @tasks)]
	  (is (= 7 (perform 1 2 5 t :test '(3 4))))))
  (deftest test-user-at-host?
	(let [f (user-at-host? "host" "user")]
	  (is (f {:user "user" :host "host"}))
	  (is (not (f {:user "hello" :host "host"}))))
	)
  (deftest test-set-options!
    (is (nil?  (:ssh-options @@*global-options*)))
    (set-options! :ssh-options "-o ConnectTimeout=3000")
    (println @@*global-options*)
    (is (= 1 (count @@*global-options*)))
    (is (= "-o ConnectTimeout=3000" (:ssh-options @@*global-options*)))
    (clear-options!))
  (deftest test-find-client-options
	(let [cluster1 {:ssh-options "-abc" :clients [ {:user "login" :host "a.domain.com" :ssh-options "-def"} ]}
            cluster2 {:addresses ["a.domain.com"]}]
	  (is (= "-abc" (find-client-options "b.domain.com" "login" cluster1 :ssh-options)))
	  (is (= "-abc" (find-client-options "a.domain.com" "alogin" cluster1 :ssh-options)))
	  (is (= "-def" (find-client-options "a.domain.com" "login" cluster1 :ssh-options)))
      (is (nil? (find-client-options "a.domain.com" "login" cluster2 :ssh-options)))
      (set-options! :ssh-options "-o ConnectTimeout=3000" :user "deploy")
      (is (= "-o ConnectTimeout=3000" (find-client-options "a.domain.com" nil cluster2 :ssh-options)))
      (is (= "deploy@a.domain.com" (ssh-client "a.domain.com" nil)))
      (clear-options!))))

(defn not-nil?
  [x]
  (not (nil? x)))
(defn myexec
  [h u c]
  (filter not-nil? c))

(deftest test-scp
  (binding [exec myexec
            *tmp-dir* "/tmp/test/"
            ]
    (let [files ["a.text" "b.txt"]]
      (is (= '("scp" "-v" "a.text" "b.txt" "user@host:/tmp")
             (scp "host" "user" {:scp-options "-v"} files "/tmp")))
	  (is (= '("scp" "a.text" "b.txt" "user@host:/tmp")
             (scp "host" "user" nil files "/tmp")))
      (is (= '("ssh" "user@host" "mv /tmp/test/* /tmp ; rm -rf /tmp/test/")
             (scp "host" "user" nil files "/tmp" :mode 755)))
      (is (= '("ssh" "user@host" "sudo mv /tmp/test/* /tmp ; rm -rf /tmp/test/")
             (scp "host" "user" nil files "/tmp" :sudo true :mode 755))))))


(deftest test-ssh
  (binding [exec myexec]
	(is (= '("ssh" "-v" "user@host" "date"))
		(ssh "host" "user" {:ssh-options "-v"} "date"))
	(is (= '("ssh" "user@host" "date"))
		(ssh "host" "user" nil "date"))))

(deftest test-rsync
  (binding [exec myexec]
	(is (= '("rsync" "-v" "src" "user@host:dst"))
		(rsync "host"  "user" {:rsync-options "-v"} "src" "dst"))
	(is (= '("rsync" "src" "user@host:dst"))
		(rsync "host"  "user" nil "src" "dst"))))
