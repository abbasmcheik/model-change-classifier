(ns mcc.rules-test
  (:require [clojure.test :refer [deftest is testing]]
            [mcc.rules :as r]))

(defn- rule-id
  "Id of the rule that matches `change` in the default table."
  [change]
  (:id (r/match-rule r/rules change)))

(defn- changed [path before after]
  {:path path :kind :changed :before before :after after})

(deftest rule-ids-unique
  (is (apply distinct? (map :id r/rules))))

(deftest every-default-rule-is-exercised
  (let [cases {:version-bump (changed [:version] "1.0.0" "1.1.0")
               :notes-edit {:path [:model :notes] :kind :removed :before "x"}
               :preprocessing-change {:path [:model :preprocessing] :kind :added :after "clahe"}
               :input-types-change (changed [:model :input-types] [:a] [:a :b])
               :platforms-change (changed [:deployment :platforms] #{:cloud} #{:cloud :edge})
               :performance-drop-large (changed [:performance :sensitivity] 0.92 0.80)
               :performance-drop-small (changed [:performance :sensitivity] 0.92 0.88)
               :performance-rise (changed [:performance :sensitivity] 0.80 0.90)
               :performance-other (changed [:performance :sensitivity] 0.92 0.915)
               :intended-use-change (changed [:intended-use] "a" "b")
               :architecture-change (changed [:model :architecture] "resnet50" "vit")
               :training-data-change (changed [:model :training-data-hash] "a" "b")
               :name-change (changed [:name] "A" "B")}]
    (is (= (set (map :id r/rules)) (set (keys cases)))
        "cases cover exactly the default rule ids")
    (doseq [[id change] cases]
      (testing (str id)
        (is (= id (rule-id change)))))))

(deftest default-rule-classes
  (let [cls (fn [change] (:class (r/match-rule r/rules change)))]
    (is (= :minor (cls (changed [:version] "1" "2"))))
    (is (= :notify (cls (changed [:name] "A" "B"))))
    (is (= :reassess (cls (changed [:intended-use] "a" "b"))))))

(deftest wildcard-matches-any-single-key
  (let [rule {:id :w :match [:performance :*] :class :minor :rationale "r"}]
    (is (= rule (r/match-rule [rule] (changed [:performance :sensitivity] 1 2))))
    (is (= rule (r/match-rule [rule] (changed [:performance :auc] 1 2))))
    (is (nil? (r/match-rule [rule] (changed [:model :auc] 1 2))))
    (testing "different length never matches"
      (is (nil? (r/match-rule [rule] (changed [:performance] 1 2))))
      (is (nil? (r/match-rule [rule] (changed [:performance :a :b] 1 2)))))))

(deftest first-match-wins
  (let [specific {:id :specific :match [:model :notes] :class :minor :rationale "s"}
        general {:id :general :match [:model :*] :class :reassess :rationale "g"}
        change {:path [:model :notes] :kind :removed :before "x"}]
    (is (= :specific (:id (r/match-rule [specific general] change))))
    (is (= :general (:id (r/match-rule [general specific] change))))))

(deftest match-rule-returns-nil-when-nothing-matches
  (is (nil? (r/match-rule r/rules (changed [:mystery :field] 1 2))))
  (is (nil? (r/match-rule [] (changed [:version] "1" "2")))))

(deftest nil-condition-is-true
  (is (true? (r/condition-holds? nil (changed [:x] 1 2))))
  (is (true? (r/condition-holds? nil {:path [:x] :kind :added :after 1}))))

(deftest drops-by-more-than
  (let [c [:drops-by-more-than 0.05]]
    (is (true? (r/condition-holds? c (changed [:p] 0.92 0.80))))
    (is (false? (r/condition-holds? c (changed [:p] 0.92 0.90))))
    (is (false? (r/condition-holds? c (changed [:p] 0.80 0.92)))
        "a rise is not a drop")))

(deftest rises-by-more-than
  (let [c [:rises-by-more-than 0.05]]
    (is (true? (r/condition-holds? c (changed [:p] 0.80 0.92))))
    (is (false? (r/condition-holds? c (changed [:p] 0.80 0.82))))
    (is (false? (r/condition-holds? c (changed [:p] 0.92 0.80)))
        "a drop is not a rise")))

(deftest conditions-need-numbers-on-a-changed-change
  (let [drop [:drops-by-more-than 0.05]
        rise [:rises-by-more-than 0.05]]
    (testing "non-numeric values"
      (is (false? (r/condition-holds? drop (changed [:p] "high" "low"))))
      (is (false? (r/condition-holds? rise (changed [:p] [:a] [:a :b])))))
    (testing "one side numeric only"
      (is (false? (r/condition-holds? drop (changed [:p] 0.9 nil))))
      (is (false? (r/condition-holds? rise (changed [:p] nil 0.9)))))
    (testing ":added and :removed"
      (is (false? (r/condition-holds? rise {:path [:p] :kind :added :after 0.9})))
      (is (false? (r/condition-holds? drop {:path [:p] :kind :removed :before 0.9}))))))

(deftest performance-rules-route-by-size-of-change
  (testing "a tiny numeric drop is caught by the last performance rule"
    (is (= :performance-other (rule-id (changed [:performance :sensitivity] 0.92 0.915)))))
  (testing "added or removed metric matches no rule"
    (is (nil? (rule-id {:path [:performance :auc] :kind :added :after 0.9})))
    (is (nil? (rule-id {:path [:performance :auc] :kind :removed :before 0.9}))))
  (testing "non-numeric metric value matches no rule"
    (is (nil? (rule-id (changed [:performance :auc] "a" "b")))))
  (testing "1 -> 1.0 is :changed but neither a drop nor a rise, so no rule"
    (is (nil? (rule-id (changed [:performance :auc] 1 1.0)))))
  (testing "ordering: the large drop rule is tried before the small one"
    (let [ids (mapv :id r/rules)]
      (is (< (.indexOf ids :performance-drop-large)
             (.indexOf ids :performance-drop-small)
             (.indexOf ids :performance-rise)
             (.indexOf ids :performance-other))))))
