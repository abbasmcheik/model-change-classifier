(ns mcc.diff-test
  (:require [clojure.edn :as edn]
            [clojure.test :refer [deftest is testing]]
            [mcc.diff :refer [diff-cards]]))

(def fixture
  "The shared old/new/changes example, read from the repo root."
  (edn/read-string (slurp "test/fixtures/basic.edn")))

(deftest fixture-test
  (is (= (:changes fixture)
         (diff-cards (:old fixture) (:new fixture)))))

(deftest identical-cards-test
  (is (= [] (diff-cards (:old fixture) (:old fixture)))))

(deftest added-removed-changed-test
  (testing "key only in new is :added, with no :before"
    (is (= [{:path [:b] :kind :added :after 2}]
           (diff-cards {:a 1} {:a 1 :b 2}))))
  (testing "key only in old is :removed, with no :after"
    (is (= [{:path [:b] :kind :removed :before 2}]
           (diff-cards {:a 1 :b 2} {:a 1}))))
  (testing "different value is :changed"
    (is (= [{:path [:a] :kind :changed :before 1 :after 5}]
           (diff-cards {:a 1} {:a 5})))))

(deftest nested-test
  (is (= [{:path [:x :y :z] :kind :changed :before "old" :after "new"}]
         (diff-cards {:x {:y {:z "old" :same 1}}}
                     {:x {:y {:z "new" :same 1}}}))))

(deftest nil-value-test
  (testing "nil in old, absent in new is :removed"
    (is (= [{:path [:a] :kind :removed :before nil}]
           (diff-cards {:a nil} {}))))
  (testing "absent in old, nil in new is :added"
    (is (= [{:path [:a] :kind :added :after nil}]
           (diff-cards {} {:a nil}))))
  (testing "nil in both is no change"
    (is (= [] (diff-cards {:a nil} {:a nil})))))

(deftest vector-and-set-are-leaves-test
  (testing "vector difference is one :changed at the vector's path"
    (is (= [{:path [:v] :kind :changed :before [1 2] :after [1 2 3]}]
           (diff-cards {:v [1 2]} {:v [1 2 3]}))))
  (testing "set difference is one :changed at the set's path"
    (is (= [{:path [:s] :kind :changed :before #{:a} :after #{:a :b}}]
           (diff-cards {:s #{:a}} {:s #{:a :b}}))))
  (testing "same elements in the same order is no change"
    (is (= [] (diff-cards {:v [1 2] :s #{:a :b}} {:v [1 2] :s #{:b :a}})))))

(deftest map-replaced-by-non-map-test
  (testing "map becomes a string"
    (is (= [{:path [:m] :kind :changed :before {:k 1} :after "flat"}]
           (diff-cards {:m {:k 1}} {:m "flat"}))))
  (testing "string becomes a map"
    (is (= [{:path [:m] :kind :changed :before "flat" :after {:k 1}}]
           (diff-cards {:m "flat"} {:m {:k 1}})))))

(deftest sorted-output-test
  (is (= [[:a] [:b] [:c]]
         (map :path (diff-cards {:c 1 :a 1 :b 1} {:c 2 :a 2 :b 2})))))
