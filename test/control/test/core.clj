(ns control.test.core
  (:use [control.core])
  (:use [clojure.test]))

(defn- arg-count [f] (let [m (first (.getDeclaredMethods (class f))) p (.getParameterTypes m)] (alength p)))
(deftest test-gen-log
  (is (= "localhost:ssh: test" (gen-log "localhost" "ssh" "test")))
  (is (= "a.domain.com:scp: hello world" (gen-log "a.domain.com" "scp" "hello world"))))

(deftest test-ssh-client
  (is (= "apple@localhost" (ssh-client "localhost" "apple")))
  (is (= "dennis@a.domain.com" (ssh-client "a.domain.com" "dennis"))))


(deftest test-task
  (try
	(is (= 0 (count tasks)))
	(task :test "test task"
		  []
		  (+ 1 2))
	(is (= 1 (count tasks)))
	(is (= 0 (count clusters)))
	(is (function? (:test tasks)))
	(is (= 2 (arg-count (:test tasks))))
	(is (= 10 ((:test tasks) 3 4)))
	(finally
	 (.without tasks :test))))

(deftest test-cluster
  (try
	(is (= 0 (count clusters)))
	(cluster :mycluster
			 :clients [{:host "a.domain.com" :user "apple"}]
			 :addresses ["a.com" "b.com"]
			 :user "dennis"
			 )
	(is (= 0 (count tasks)))
	(is (= 1 (count clusters)))
	(let [m (:mycluster clusters)]
	  (is (= :mycluster (:name m)))
	  (is (= [{:host "a.domain.com" :user "apple"}] (:clients m)))
	  (is (= "dennis" (:user m)))
	  (is (= ["a.com" "b.com"] (:addresses m))))
	(finally
	 (.without clusters :mycluster))))

 (defmacro with-private-fns [[ns fns] & tests]
  "Refers private fns from ns and runs tests in context."
    `(let ~(reduce #(conj %1 %2 `(ns-resolve '~ns '~%2)) [] fns)
         ~@tests))

(with-private-fns [control.core [perform]]
  (deftest test-perform
	(try
	  (task :test "test-task"
			[a b]
			(+ a b))
	  (let [t (:test tasks)]
		(is (= 10 (perform 1 2 t :test '(3 4)))))
	  (finally
		 (.without tasks :test)))))

