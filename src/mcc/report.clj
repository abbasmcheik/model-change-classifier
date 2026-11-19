(ns mcc.report
  "Turns a classification result into text (for people) or EDN (for programs).
  Depends only on the shape of the classification result, not on other mcc
  namespaces."
  (:require [clojure.string :as str]))

(defn- path->str
  "Renders a path vector readably: [:performance :sensitivity] => \"performance.sensitivity\"."
  [path]
  (str/join "." (map #(if (keyword? %) (name %) (str %)) path)))

(defn- change-line
  "The before/after line. Only the sides that exist are shown, chosen by :kind
  (we cannot test for a missing key with `nil`, because nil can be a real value)."
  [{:keys [kind before after]}]
  (case kind
    :added   (str "-> " (pr-str after))
    :removed (str (pr-str before) " ->")
    (str (pr-str before) " -> " (pr-str after))))

(defn- change-block
  "One entry of the result as a few indented lines."
  [{:keys [change class rule-id rationale]}]
  (str/join "\n"
            [(str (str/upper-case (name class)) "  " (path->str (:path change)))
             (str "  kind:   " (name (:kind change)))
             (str "  change: " (change-line change))
             (str "  rule:   " (if rule-id (str rule-id) "(no rule)"))
             (str "  why:    " rationale)]))

(defn ->text
  "Human-readable report. First line is the verdict, then one block per
  change in input order. Plain ASCII, no colour."
  [{:keys [verdict changes]}]
  (let [header (str "Verdict: " (str/upper-case (name verdict)))]
    (if (empty? changes)
      (str header "\n\nNo changes.")
      (str header "\n\n" (str/join "\n\n" (map change-block changes))))))

(defn ->edn
  "The result as an EDN string. Read it back with clojure.edn/read-string."
  [result]
  (pr-str result))
