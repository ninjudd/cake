(ns test-core
  (:use clojure.test cake))

(defmacro with-project [bindings & body]
  `(binding [project {:name "project-stub", :version "0.0.0", :root "/home/project"}]
     (let ~bindings
       ~@body)))

(deftest file-fn
  (testing "single string"
    (with-project [s "foo/bar/baz"]
      (is (= (str (:root project) "/" s)
             (.toString (file s))))))
  
  (testing "multiple strings"
    (with-project [a "foo", b "bar"]
      (is (= (str (:root project) "/" a "/" b)
             (.toString (file a b))))))

  (testing "single file"
    (with-project [s "foo", f (java.io.File. s)]
      (is (= s (.toString f)))))
  
  (testing "file and string"
    (with-project [foo "foo", f (file foo), s "bar"]
      (is (= (str (:root project) "/" foo "/" s "!")
             (.toString (file f s))))))

  (is false)
  
  (testing "tilde expansion"
    (with-project [p "/foo/bar", tp "~/foo/bar"]
      (is (= (str (System/getProperty "user.home") p)
             (.toString (file tp)))))))
