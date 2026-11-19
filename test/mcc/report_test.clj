(ns mcc.report-test
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [mcc.report :as report]))

;; Results are built by hand so this test needs no other mcc namespace.
(def changed-entry
  {:change {:path [:performance :sensitivity] :kind :changed :before 0.92 :after 0.8}
   :class :reassess
   :rule-id :sensitivity-drop-large
   :rationale "Large drop in sensitivity"})

(def added-entry
  {:change {:path [:deployment :region] :kind :added :after "eu"}
   :class :notify
   :rule-id :region-added
   :rationale "New region"})

(def removed-entry
  {:change {:path [:model :notes] :kind :removed :before "old notes"}
   :class :minor
   :rule-id :notes-removed
   :rationale "Notes dropped"})

(def unmatched-entry
  {:change {:path [:extra :flag] :kind :changed :before true :after false}
   :class :reassess
   :rule-id nil
   :rationale "No rule covers this change"})

(deftest verdict-line
  (let [lines (str/split-lines (report/->text {:verdict :reassess
                                               :changes [changed-entry]}))]
    (is (= "Verdict: REASSESS" (first lines)))
    (is (= "" (second lines)))))

(deftest change-shows-all-pieces
  (let [text (report/->text {:verdict :reassess :changes [changed-entry]})]
    (is (str/includes? text "REASSESS  performance.sensitivity"))
    (is (str/includes? text "kind:   changed"))
    (is (str/includes? text "0.92 -> 0.8"))
    (is (str/includes? text ":sensitivity-drop-large"))
    (is (str/includes? text "Large drop in sensitivity"))))

(deftest added-and-removed-omit-missing-side
  (let [text (report/->text {:verdict :notify
                             :changes [added-entry removed-entry]})]
    (testing "added shows only the after side"
      (is (str/includes? text "kind:   added"))
      (is (str/includes? text "change: -> \"eu\"")))
    (testing "removed shows only the before side"
      (is (str/includes? text "kind:   removed"))
      (is (str/includes? text "change: \"old notes\" ->")))))

(deftest unmatched-change-shows-no-rule
  (let [text (report/->text {:verdict :reassess :changes [unmatched-entry]})]
    (is (str/includes? text "rule:   (no rule)"))
    (is (str/includes? text "No rule covers this change"))))

(deftest changes-keep-input-order
  (let [text (report/->text {:verdict :reassess
                             :changes [removed-entry changed-entry added-entry]})
        idx  #(str/index-of text %)]
    (is (< (idx "model.notes") (idx "performance.sensitivity") (idx "deployment.region")))))

(deftest empty-result
  (is (= "Verdict: MINOR\n\nNo changes."
         (report/->text {:verdict :minor :changes []}))))

(deftest text-is-plain-ascii
  (let [text (report/->text {:verdict :reassess
                             :changes [changed-entry added-entry removed-entry unmatched-entry]})]
    (is (every? #(< (int %) 128) text))))

(deftest edn-round-trip
  (let [result {:verdict :reassess
                :changes [{:change {:path [:deployment :platforms]
                                    :kind :changed
                                    :before #{:cloud}
                                    :after #{:cloud :edge}}
                           :class :reassess
                           :rule-id nil
                           :rationale "No rule covers this change"}
                          {:change {:path [:model :input-types]
                                    :kind :changed
                                    :before [:fundus-photo]
                                    :after [:fundus-photo :oct]}
                           :class :notify
                           :rule-id :input-types-changed
                           :rationale "Inputs changed"}]}]
    (is (= result (edn/read-string (report/->edn result))))
    (is (set? (get-in (edn/read-string (report/->edn result))
                      [:changes 0 :change :after])))
    (is (vector? (get-in (edn/read-string (report/->edn result))
                         [:changes 1 :change :after])))))
