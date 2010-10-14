(ns test-foo-bar
  (:use cake clojure.test))

(deftest #^{:tags [:foo]} foo
  (println ":foo:")
  (is (= 1 1)))

(deftest #^{:tags [:bar]} bar
  (println ":bar:")
  (is (= 1 1)))

(deftest #^{:tags [:foo :bar]} foo-bar
  (println ":foobar:")
  (is (= 1 1)))