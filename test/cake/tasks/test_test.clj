(ns cake.tasks.test-test
  (:use clojure.test helpers cake))

(use-fixtures :once in-test-project)

(deftest test-context
  (is (= 'test (:context *project*))))

(deftest test-tags
  (let [results (cake "test" ":foo" ":bar" ":baz")]
    (is (re-find #":baz:"    (:out results)))
    (is (re-find #":foo:"    (:out results)))
    (is (re-find #":bar:"    (:out results)))
    (is (re-find #":foobar:" (:out results))))
  (let [results (cake "test" ":foo" ":bar")]
    (is (not (re-find #":baz:" (:out results))))
    (is (re-find #":foo:"    (:out results)))
    (is (re-find #":bar:"    (:out results)))
    (is (re-find #":foobar:" (:out results))))
  (let [results (cake "test" ":foo")]
    (is (not (re-find #":baz:" (:out results))))
    (is (not (re-find #":bar:" (:out results))))
    (is (re-find #":foo:"    (:out results)))
    (is (re-find #":foobar:" (:out results))))
  (let [results (cake "test" ":baz")]
    (is (not (re-find #":foo:"    (:out results))))
    (is (not (re-find #":bar:"    (:out results))))
    (is (not (re-find #":foobar:" (:out results))))
    (is (re-find #":baz:" (:out results))))
  (let [results (cake "test" ":bar")]
    (is (not (re-find #":baz:" (:out results))))
    (is (not (re-find #":foo:" (:out results))))
    (is (re-find #":bar:"    (:out results)))
    (is (re-find #":foobar:" (:out results)))))


(deftest test-namespaces
  (let [results (cake "test" "test-foo-bar")]
    (is (not (re-find #":baz:" (:out results))))
    (is (re-find #":foo:"    (:out results)))
    (is (re-find #":bar:"    (:out results)))
    (is (re-find #":foobar:" (:out results))))
  (let [results (cake "test" "test-baz")]
    (is (not (re-find #":foo:"    (:out results))))
    (is (not (re-find #":bar:"    (:out results))))
    (is (not (re-find #":foobar:" (:out results))))
    (is (re-find #":baz:" (:out results)))))

(deftest test-functions
  (let [results (cake "test" "test-foo-bar/foo")]
    (is (not (re-find #":baz:"    (:out results))))
    (is (not (re-find #":bar:"    (:out results))))
    (is (not (re-find #":foobar:" (:out results))))
    (is (re-find #":foo:"    (:out results))))
  (let [results (cake "test" "test-foo-bar/bar" "test-foo-bar/foo-bar")]
    (is (not (re-find #":baz:" (:out results))))
    (is (not (re-find #":foo:" (:out results))))
    (is (re-find #":bar:"    (:out results)))
    (is (re-find #":foobar:" (:out results))))
  (let [results (cake "test" "test-baz/baz")]
    (is (not (re-find #":foo:" (:out results))))
    (is (not (re-find #":bar:"    (:out results))))
    (is (not (re-find #":foobar:" (:out results))))
    (is (re-find #":baz:" (:out results))))
  (let [results (cake "test" "test-foo-bar/bar" "test-baz/baz")]
    (is (not (re-find #":foobar:" (:out results))))
    (is (not (re-find #":foo:"    (:out results))))
    (is (re-find #":bar:" (:out results)))
    (is (re-find #":baz:" (:out results)))))

(deftest test-combination
  (let [results (cake "test" "test-foo-bar/foo" ":baz")]
    (is (not (re-find #":bar:"    (:out results))))
    (is (not (re-find #":foobar:" (:out results))))
    (is (re-find #":foo:"    (:out results)))
    (is (re-find #":baz:"    (:out results))))
  (let [results (cake "test" "test-foo-bar/bar" ":bar" "test-baz")]
    (is (not (re-find #":foo:" (:out results))))
    (is (re-find #":bar:"    (:out results)))
    (is (re-find #":foobar:" (:out results)))
    (is (re-find #":baz:"    (:out results)))))
