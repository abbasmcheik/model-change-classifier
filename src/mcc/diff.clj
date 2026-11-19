(ns mcc.diff
  "Compare two model cards and list what changed, as a sorted vector of
  Change maps (see docs/CONTRACT.md).")

(declare diff-maps)

(defn- diff-key
  "Changes for one key. `path` already ends with that key. `old-has?` and
  `new-has?` say whether the key is present, so a nil value still counts."
  [path old-has? old-val new-has? new-val]
  (cond
    ;; Key only in the old card.
    (not new-has?) [{:path path :kind :removed :before old-val}]
    ;; Key only in the new card.
    (not old-has?) [{:path path :kind :added :after new-val}]
    ;; Two maps: look inside them.
    (and (map? old-val) (map? new-val)) (diff-maps path old-val new-val)
    ;; Anything else (numbers, strings, vectors, sets, map vs non-map)
    ;; is a leaf: equal means no change, otherwise one :changed.
    (= old-val new-val) []
    :else [{:path path :kind :changed :before old-val :after new-val}]))

(defn- diff-maps
  "Changes between two maps found at `path` (a vector of keys from the root)."
  [path old-map new-map]
  (let [all-keys (distinct (concat (keys old-map) (keys new-map)))]
    (mapcat (fn [k]
              (diff-key (conj path k)
                        (contains? old-map k) (get old-map k)
                        (contains? new-map k) (get new-map k)))
            all-keys)))

(defn diff-cards
  "Return a vector of Change maps describing how `new` differs from `old`.
  Maps are walked recursively; vectors and sets are compared as whole values.
  The result is sorted by the string form of each path, and is [] when the
  cards are identical."
  [old new]
  (->> (diff-maps [] old new)
       (sort-by (comp str :path))
       vec))
