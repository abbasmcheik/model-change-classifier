(ns mcc.classify
  "Turn a list of changes into a classification result."
  (:require [mcc.rules :as rules]))

(def severity
  "Numeric order of the classes, used to find the worst one."
  {:minor 0 :notify 1 :reassess 2})

(defn max-class
  "Most severe class in `classes`. An empty collection gives :minor."
  [classes]
  (reduce (fn [worst c]
            (if (> (severity c) (severity worst)) c worst))
          :minor
          classes))

(defn- classify-change
  "Classify one change. A change no rule covers escalates to :reassess."
  [rule-table change]
  (if-let [rule (rules/match-rule rule-table change)]
    {:change change
     :class (:class rule)
     :rule-id (:id rule)
     :rationale (:rationale rule)}
    {:change change
     :class :reassess
     :rule-id nil
     :rationale "No rule covers this change"}))

(defn classify-changes
  "Classify every change with `rule-table`. Returns {:verdict class :changes
  [...]} where :changes keeps the input order and :verdict is the most severe
  class (:minor when there are no changes)."
  [rule-table changes]
  (let [classified (mapv #(classify-change rule-table %) changes)]
    {:verdict (max-class (map :class classified))
     :changes classified}))
