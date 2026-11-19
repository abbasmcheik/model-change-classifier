(ns mcc.spec
  "clojure.spec definitions for a model card, plus two small helpers.
  Maps are open: s/keys only checks the keys it lists, so extra keys pass."
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]))

;; --- Small building blocks -------------------------------------------------

;; A number between 0 and 1 inclusive (used for sensitivity / specificity).
(s/def ::ratio (s/and number? #(<= 0 % 1)))

;; Vectors and sets are checked with vector?/set? first, because
;; s/coll-of alone would also accept lists (or any collection).
(s/def ::keyword-vector (s/and vector? (s/coll-of keyword?)))
(s/def ::keyword-set (s/and set? (s/coll-of keyword?)))

;; --- Top-level string fields -----------------------------------------------

(s/def ::name string?)
(s/def ::version string?)
(s/def ::intended-use string?)

;; --- :model ----------------------------------------------------------------

(s/def ::architecture string?)
(s/def ::training-data-hash string?)
(s/def ::input-types ::keyword-vector)
(s/def ::notes string?)
(s/def ::preprocessing string?)

(s/def ::model
  (s/keys :req-un [::architecture ::training-data-hash ::input-types]
          :opt-un [::notes ::preprocessing]))

;; --- :performance ----------------------------------------------------------

(s/def ::sensitivity ::ratio)
(s/def ::specificity ::ratio)

(s/def ::performance
  (s/keys :req-un [::sensitivity ::specificity]))

;; --- :deployment -----------------------------------------------------------

(s/def ::platforms ::keyword-set)

(s/def ::deployment
  (s/keys :req-un [::platforms]))

;; --- The whole card --------------------------------------------------------

(s/def ::card
  (s/keys :req-un [::name ::version ::intended-use
                   ::model ::performance ::deployment]))

;; --- Public API ------------------------------------------------------------

(defn valid-card?
  "Returns true when `card` conforms to the model card spec, else false."
  [card]
  (s/valid? ::card card))

(defn- shorten
  "Cuts `text` to at most `n` characters, adding ... when it was cut."
  [text n]
  (if (> (count text) n)
    (str (subs text 0 n) "...")
    text))

(defn- contains-check
  "The (contains? % :k) form inside `pred`, or nil. A failed check like that
  means a required key is missing."
  [pred]
  (->> (tree-seq coll? seq pred)
       (filter #(and (seq? %) (= 'clojure.core/contains? (first %))))
       first))

(defn- problem->str
  "One spec problem as a short phrase. For a missing key we do not print the
  value, because it is the whole map."
  [{:keys [path pred val]}]
  (let [where (if (empty? path) "card" (str/join "." (map name path)))]
    (if-let [check (contains-check pred)]
      (str where ": missing key " (last check))
      (str where ": failed "
           (str/replace (pr-str pred) "clojure.core/" "")
           " (got " (shorten (pr-str val) 30) ")"))))

(defn explain-card
  "Returns nil when `card` is valid, otherwise a short one-line string listing
  what is wrong, e.g. \"performance.sensitivity: failed (<= 0 % 1) (got 1.5)\"."
  [card]
  (when-not (valid-card? card)
    (->> (::s/problems (s/explain-data ::card card))
         (map problem->str)
         (str/join "; "))))
