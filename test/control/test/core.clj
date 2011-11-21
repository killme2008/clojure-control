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


(deftest test-task
  (is (= 0 (count @tasks)))
  (deftask :test "test task"
	[]
	(+ 1 2))
  (is (= 1 (count @tasks)))
  (is (= 0 (count @clusters)))
  (is (function? (:test @tasks)))
  (is (= 3 (arg-count (:test @tasks))))
  (is (= 15 ((:test @tasks) 3 4 5))))


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

(with-private-fns [control.core [perform spawn await-process user-at-host? find-client-options make-cmd-array]]
  (deftest test-make-cmd-array
	(is (= ["ssh" "-v" "-p 44" "user@host"] (make-cmd-array "ssh" ["-v" "-p 44"] ["user@host"])))
	(is (= ["rsync" "-arz" "--delete" "src" "user@host::share"] (make-cmd-array "rsync" ["-arz" "--delete"] ["src" "user@host::share"]))))
  (deftest test-perform
	(deftask :test "test-task"
	  [a b]
	  (+ a b))
	(let [t (:test @tasks)]
	  (is (= 15 (perform 1 2 5 t :test '(3 4))))))
  (deftest test-spawn
	(let [pagent (spawn (into-array String ["echo" "hello"]))]
	  (let [status (await-process pagent)
			execp @pagent]
		(is (= 0 status))
		(is (= "hello" (:stdout execp)))
		(is (blank? (:stderr execp)))
		)))
  (deftest test-user-at-host?
	(let [f (user-at-host? "host" "user")]
	  (is (f {:user "user" :host "host"}))
	  (is (not (f {:user "hello" :host "host"}))))
	)
  (deftest test-find-client-options
	(let [cluster {:ssh-options "-abc" :clients [ {:user "login" :host "a.domain.com" :ssh-options "-def"} ]}]
	  (is (= "-abc" (find-client-options "b.domain.com" "login" cluster :ssh-options)))
	  (is (= "-abc" (find-client-options "a.domain.com" "alogin" cluster :ssh-options)))
	  (is (= "-def" (find-client-options "a.domain.com" "login" cluster :ssh-options))))))

(defn not-nil?
  [x]
  (not (nil? x)))
(defn myexec
  [h u c]
  (filter not-nil? c))

(deftest test-scp
  (binding [exec myexec]
    (let [files ["a.text" "b.txt"]]
      (is (= '("scp" "-v" "a.text" "b.txt" "user@host:/tmp")
             (scp "host" "user" {:scp-options "-v"} files "/tmp")))
	  (is (= '("scp" "a.text" "b.txt" "user@host:/tmp")
             (scp "host" "user" nil files "/tmp"))))))

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
