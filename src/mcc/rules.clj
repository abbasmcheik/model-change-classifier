(ns mcc.rules
  "Rule table and rule matching.

  NOTE: the default rules below are ILLUSTRATIVE. They are not real
  regulatory logic, only a worked example of how a rule table looks.")

(def rules
  "Default rule table. Plain EDN data. Rules are tried in order and the first
  match wins, so specific rules come before general ones."
  [{:id :version-bump
    :match [:version]
    :class :minor
    :rationale "A version label change alone does not alter behaviour."}
   {:id :notes-edit
    :match [:model :notes]
    :class :minor
    :rationale "Free-text notes do not affect the model."}
   {:id :preprocessing-change
    :match [:model :preprocessing]
    :class :notify
    :rationale "Preprocessing changes can shift model inputs."}
   {:id :input-types-change
    :match [:model :input-types]
    :class :reassess
    :rationale "New or removed input types change what the model sees."}
   {:id :platforms-change
    :match [:deployment :platforms]
    :class :notify
    :rationale "Deployment platforms changed."}
   ;; Performance rules assume higher is better. Big drop first, then small
   ;; drop, then a rise, then a tiny drop. Added, removed and non-numeric
   ;; metric changes match nothing, so classify escalates them.
   {:id :performance-drop-large
    :match [:performance :*]
    :when [:drops-by-more-than 0.05]
    :class :reassess
    :rationale "Large drop in a performance metric."}
   {:id :performance-drop-small
    :match [:performance :*]
    :when [:drops-by-more-than 0.02]
    :class :notify
    :rationale "Small drop in a performance metric."}
   {:id :performance-rise
    :match [:performance :*]
    :when [:rises-by-more-than 0.0]
    :class :minor
    :rationale "A performance metric improved."}
   {:id :performance-other
    :match [:performance :*]
    :when [:drops-by-more-than 0.0]
    :class :minor
    :rationale "Negligible drop in a performance metric."}
   {:id :intended-use-change
    :match [:intended-use]
    :class :reassess
    :rationale "A new intended use needs a fresh assessment."}
   {:id :architecture-change
    :match [:model :architecture]
    :class :reassess
    :rationale "A different architecture is a different model."}
   {:id :training-data-change
    :match [:model :training-data-hash]
    :class :reassess
    :rationale "Different training data can change model behaviour."}
   {:id :name-change
    :match [:name]
    :class :notify
    :rationale "The product name changed."}])

(defn- path-matches?
  "True when `pattern` and `path` have the same length and every pattern
  element is :* or equal to the path element at that position."
  [pattern path]
  (and (= (count pattern) (count path))
       (every? true?
               (map (fn [p k] (or (= p :*) (= p k))) pattern path))))

(defn condition-holds?
  "Evaluate a rule condition against a change. A nil condition is always true.
  Numeric conditions need a :changed change with numeric :before and :after;
  anything else is false."
  [condition change]
  (if (nil? condition)
    true
    (let [[op n] condition
          {:keys [kind before after]} change]
      (if (and (= kind :changed) (number? before) (number? after))
        (case op
          :drops-by-more-than (> (- before after) n)
          :rises-by-more-than (> (- after before) n)
          false)
        false))))

(defn match-rule
  "Return the first rule whose :match fits the change path and whose :when
  holds, or nil when no rule applies."
  [rules change]
  (first (filter (fn [rule]
                   (and (path-matches? (:match rule) (:path change))
                        (condition-holds? (:when rule) change)))
                 rules)))
