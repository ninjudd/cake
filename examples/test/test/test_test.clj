(ns test-test
  (:use cake clojure.test))

(deftest #^{:tags [:foo]} tag-foo
  (println ":foo:")
  (is (= 1 1)))

(deftest #^{:tags [:bar]} tag-bar
  (println ":bar:")
  (is (= 1 1)))

(deftest #^{:tags [:foo :bar]} tag-foo-and-bar
  (println ":foobar:")
  (is (= 1 1)))