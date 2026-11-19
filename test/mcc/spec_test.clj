(ns mcc.spec-test
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [mcc.spec :as spec]))

;; The shared fixture holds an :old and a :new card (both valid).
(def fixture (edn/read-string (slurp "test/fixtures/basic.edn")))

(def card (:old fixture))

(defn invalid-with-explanation?
  "True when `c` is invalid and explain-card gives a non-blank string."
  [c]
  (and (not (spec/valid-card? c))
       (string? (spec/explain-card c))
       (seq (spec/explain-card c))))

(deftest fixture-cards-are-valid
  (is (spec/valid-card? (:old fixture)))
  (is (spec/valid-card? (:new fixture))))

(deftest explain-is-nil-for-valid-cards
  (is (nil? (spec/explain-card (:old fixture))))
  (is (nil? (spec/explain-card (:new fixture)))))

(deftest missing-required-keys-are-invalid
  (testing "missing top-level key"
    (is (invalid-with-explanation? (dissoc card :name))))
  (testing "missing nested key"
    (is (invalid-with-explanation? (update card :model dissoc :architecture)))
    (is (invalid-with-explanation? (update card :performance dissoc :sensitivity)))
    (is (invalid-with-explanation? (update card :deployment dissoc :platforms)))))

(deftest wrong-types-are-invalid
  (is (invalid-with-explanation? (assoc card :version 1.0)))
  (is (invalid-with-explanation? (assoc-in card [:model :architecture] :resnet50)))
  (is (invalid-with-explanation? (assoc-in card [:model :notes] 42)))
  (is (invalid-with-explanation? (assoc-in card [:performance :sensitivity] "high")))
  (is (invalid-with-explanation? (assoc-in card [:model :input-types] [:fundus-photo "oct"]))))

(deftest not-a-map-is-invalid
  (is (invalid-with-explanation? nil))
  (is (invalid-with-explanation? "a card")))

(deftest out-of-range-performance-is-invalid
  (is (invalid-with-explanation? (assoc-in card [:performance :sensitivity] 1.5)))
  (is (invalid-with-explanation? (assoc-in card [:performance :specificity] -0.1)))
  (testing "boundaries 0 and 1 are allowed"
    (is (spec/valid-card? (assoc-in card [:performance :sensitivity] 1)))
    (is (spec/valid-card? (assoc-in card [:performance :specificity] 0)))))

(deftest collection-kinds-are-enforced
  (testing "input-types must be a vector"
    (is (invalid-with-explanation? (assoc-in card [:model :input-types] '(:fundus-photo))))
    (is (invalid-with-explanation? (assoc-in card [:model :input-types] #{:fundus-photo}))))
  (testing "platforms must be a set"
    (is (invalid-with-explanation? (assoc-in card [:deployment :platforms] [:cloud])))))

(deftest extra-keys-are-allowed
  (is (spec/valid-card? (assoc card :regulatory-region "EU")))
  (is (spec/valid-card? (assoc-in card [:model :quantized] true)))
  (is (spec/valid-card? (assoc-in card [:performance :auc] 0.95)))
  (is (spec/valid-card? (assoc-in card [:deployment :region] :eu))))

(deftest explanations-are-short-and-single-line
  (testing "a missing key is named, and the card is not dumped"
    (is (= "card: missing key :version" (spec/explain-card (dissoc card :version))))
    (is (= "model: missing key :architecture"
           (spec/explain-card (update card :model dissoc :architecture)))))
  (testing "a bad value is shown with its path"
    (is (= "performance.sensitivity: failed (fn [%] (<= 0 % 1)) (got 1.5)"
           (spec/explain-card (assoc-in card [:performance :sensitivity] 1.5)))))
  (testing "never more than one line, even with several problems"
    (is (not (str/includes? (spec/explain-card {}) "\n")))))

(deftest optional-model-keys-may-be-absent
  (is (spec/valid-card? (update card :model dissoc :notes :preprocessing))))
