(ns mcc.classify-test
  (:require [clojure.edn :as edn]
            [clojure.test :refer [deftest is testing]]
            [mcc.classify :as c]
            [mcc.rules :as r]))

;; Path is relative to the repo root, where the tests are run.
(def fixture (edn/read-string (slurp "test/fixtures/basic.edn")))

(defn- changed [path before after]
  {:path path :kind :changed :before before :after after})

(deftest severity-order
  (is (< (c/severity :minor) (c/severity :notify) (c/severity :reassess))))

(deftest max-class-picks-most-severe
  (is (= :minor (c/max-class [])))
  (is (= :minor (c/max-class [:minor :minor])))
  (is (= :notify (c/max-class [:minor :notify :minor])))
  (is (= :reassess (c/max-class [:notify :reassess :minor])))
  (is (= :reassess (c/max-class (list :reassess)))))

(deftest empty-change-list-is-minor
  (is (= {:verdict :minor :changes []} (c/classify-changes r/rules []))))

(deftest unmatched-change-escalates
  (let [change (changed [:mystery :field] 1 2)
        result (c/classify-changes r/rules [change])]
    (is (= {:change change
            :class :reassess
            :rule-id nil
            :rationale "No rule covers this change"}
           (first (:changes result))))
    (is (= :reassess (:verdict result)))))

(deftest unmatched-change-drags-verdict-up
  (let [result (c/classify-changes r/rules
                                   [(changed [:version] "1" "2")
                                    (changed [:mystery :field] 1 2)])]
    (is (= [:minor :reassess] (mapv :class (:changes result))))
    (is (= :reassess (:verdict result)))))

(deftest result-keeps-input-order-and-carries-rule-data
  (let [a (changed [:name] "A" "B")
        b (changed [:version] "1" "2")
        result (c/classify-changes r/rules [a b])]
    (is (= [a b] (mapv :change (:changes result))))
    (is (= [:name-change :version-bump] (mapv :rule-id (:changes result))))
    (is (= [:notify :minor] (mapv :class (:changes result))))
    (is (every? string? (map :rationale (:changes result))))
    (is (= :notify (:verdict result)))))

(deftest verdict-is-worst-class
  (let [result (c/classify-changes
                r/rules
                [(changed [:version] "1" "2")
                 (changed [:performance :sensitivity] 0.92 0.88)])]
    (is (= [:minor :notify] (mapv :class (:changes result))))
    (is (= :notify (:verdict result)))))

(deftest fixture-changes
  (let [result (c/classify-changes r/rules (:changes fixture))
        by-path (into {} (map (juxt (comp :path :change) :class) (:changes result)))]
    (is (= 6 (count (:changes result))))
    (is (= {[:deployment :platforms] :notify
            [:model :input-types] :reassess
            [:model :notes] :minor
            [:model :preprocessing] :notify
            [:performance :sensitivity] :reassess
            [:version] :minor}
           by-path))
    (is (= (map :change (:changes result)) (:changes fixture))
        "order preserved")
    (is (= :reassess (:verdict result)))))

(deftest custom-rule-table
  (testing "classify uses the table it is given"
    (let [table [{:id :all :match [:version] :class :notify :rationale "custom"}]
          result (c/classify-changes table [(changed [:version] "1" "2")])]
      (is (= :notify (:verdict result)))
      (is (= :all (:rule-id (first (:changes result))))))))

(deftest unmatched-performance-changes-escalate
  (doseq [change [{:path [:performance :auc] :kind :added :after 0.9}
                  {:path [:performance :auc] :kind :removed :before 0.9}
                  (changed [:performance :auc] "a" "b")
                  (changed [:performance :auc] 1 1.0)]]
    (testing (pr-str change)
      (let [result (c/classify-changes r/rules [change])
            entry (first (:changes result))]
        (is (= :reassess (:class entry)))
        (is (nil? (:rule-id entry)))
        (is (= "No rule covers this change" (:rationale entry)))
        (is (= :reassess (:verdict result)))))))

(deftest tiny-performance-drop-stays-minor
  (let [result (c/classify-changes r/rules [(changed [:performance :sensitivity] 0.92 0.915)])]
    (is (= :performance-other (:rule-id (first (:changes result)))))
    (is (= :minor (:verdict result)))))
