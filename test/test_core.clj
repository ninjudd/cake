(ns test-core
  (:use clojure.test bake))

(defmacro project-let [bindings & body]
  `(binding [project {:name "project-stub", :version "0.0.0", :root "/home/project"}]
     (let ~bindings
       ~@body)))

(deftest file-fn
  (testing "single string"
    (project-let [s "foo/bar/baz"]
      (is (= (str (:root project) "/" s)
             (.toString (file s))))))
  
  (testing "multiple strings"
    (project-let [a "foo", b "bar"]
      (is (= (str (:root project) "/" a "/" b)
             (.toString (file a b))))))

  ;; working here
  (comment testing "single file"
    (project-let [s "foo", f (java.io.File. s)]
      (is (= (str (:root project) "/" s)
             (.toString (file f))))))
  
  (testing "file and string")
  
  (testing "tilde expansion")

  (testing "home directory"))


