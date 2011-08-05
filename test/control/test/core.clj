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
  (is (= "localhost:ssh: test" (gen-log "localhost" "ssh" "test")))
  (is (= "a.domain.com:scp: hello world" (gen-log "a.domain.com" "scp" "hello world"))))

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
  (is (= 2 (arg-count (:test @tasks))))
  (is (= 10 ((:test @tasks) 3 4))))


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

(with-private-fns [control.core [perform spawn await-process]]
  (deftest test-perform
	(deftask :test "test-task"
		  [a b]
		  (+ a b))
	(let [t (:test @tasks)]
	  (is (= 10 (perform 1 2 t :test '(3 4))))))
  (deftest test-spawn
	(let [pagent (spawn (into-array String ["echo" "hello"]))]
	  (let [status (await-process pagent)
			execp @pagent]
		(is (= 0 status))
		(is (= "hello" (:stdout execp)))
		(is (blank? (:stderr execp)))
	))))

(deftest test-scp
  (binding [exec (fn [h u c] c)]
    (let [files ["a.text" "b.txt"]]
      (is (= '("scp" "a.text" "b.txt" "user@host:/tmp")
             (scp "host" "user" files "/tmp"))))))
