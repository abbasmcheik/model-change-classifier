# Contract (current version)

Shared shapes and function signatures. Modules are written against this file,
so change it only deliberately. Everything is plain data and plain functions.
No macros.

## Model card

EDN map. Required keys (checked by `mcc.spec`):

```clojure
{:name "ExampleDR"                       ; string
 :version "1.0.0"                         ; string
 :intended-use "..."                      ; string
 :model {:architecture "resnet50"         ; string
         :training-data-hash "a1b2c3"     ; string
         :input-types [:fundus-photo]     ; vector of keywords
         ;; optional: :notes string, :preprocessing string
         }
 :performance {:sensitivity 0.92          ; number in [0,1]
                :specificity 0.88}        ; number in [0,1]
 :deployment {:platforms #{:cloud}}}      ; set of keywords
```

Extra keys are allowed (open maps). That is how "unknown field" changes arise.

## Change

```clojure
{:path [:performance :sensitivity]   ; vector of keys from the root
 :kind :changed                      ; :added | :removed | :changed
 :before 0.92                        ; absent when :kind is :added
 :after 0.90}                        ; absent when :kind is :removed
```

Semantics of the diff:

- Maps are walked recursively. A key only in new is `:added`, only in old is
  `:removed`, in both with different values is recursed into (if both are
  maps) or `:changed`.
- **Vectors and sets are atomic leaves.** Compared with `=`, never diffed per
  element. A different vector or set is one `:changed` at that path.
- A whole map added or removed under a key is one `:added` or `:removed` at
  that key, not one change per entry.
- A map replaced by a non-map (or the reverse) is one `:changed` at that path.
- Output is sorted with `(sort-by (comp str :path) changes)`, so it is
  deterministic.
- Identical cards give `[]`.
- **Implementation:** a small recursive walk over the two maps. Use
  `contains?` for key presence (so a key whose value is `nil` still counts as
  present). Do **not** use `clojure.data/diff`: it diffs vectors and sets per
  element, which contradicts the atomic-leaf rule above.
- **Test values must not sit on a threshold boundary.** Floating point makes
  `(- 0.92 0.90)` slightly more than `0.02`. Examples and tests use values
  clearly on one side of every threshold.

## Rule

Plain EDN data (no functions), so rules print and diff cleanly.

```clojure
{:id :performance-drop-small          ; keyword, unique
 :match [:performance :*]             ; path pattern
 :when [:drops-by-more-than 0.02]     ; optional condition, see below
 :class :notify                       ; :minor | :notify | :reassess
 :rationale "Small drop in a performance metric."}
```

- **Path pattern:** vector of keys, same length as the change path. The
  keyword `:*` matches any single key. No other wildcards.
- **Condition `:when`** (optional, absent means always true). Vector `[op n]`,
  evaluated only for `:changed` changes where `:before` and `:after` are both
  numbers; otherwise the condition is false:
  - `[:drops-by-more-than n]` true when `(> (- before after) n)`
  - `[:rises-by-more-than n]` true when `(> (- after before) n)`
- **Matching:** rules are tried in vector order. First match wins. Put
  specific rules before general ones.
- An unknown condition op evaluates to false.
- Numeric rules assume higher is better for `[:performance :*]` metrics.
- Added, removed or non-numeric changes under `[:performance :*]` match no
  rule on purpose, so they escalate to `:reassess`. Note `1` vs `1.0` is a
  `:changed` change (`=` is false) and also lands unmatched.
- Optional `:citation` field is reserved for future use and ignored for now.

## Severity

`:minor` < `:notify` < `:reassess`. Overall verdict is the maximum class of
all changes. No changes gives `:minor`.

**Unmatched change gets `:reassess`** with `:rule-id nil` and rationale
`"No rule covers this change"`. Never pass silently.

## Classification result

```clojure
{:verdict :reassess
 :changes [{:change {...change map...}
            :class :notify
            :rule-id :performance-drop-small   ; nil when unmatched
            :rationale "..."}
           ...]}                                ; same order as input changes
```

## Function signatures

```clojure
;; src/mcc/diff.clj
(mcc.diff/diff-cards old new)            ; => vector of Change, sorted

;; src/mcc/rules.clj
mcc.rules/rules                          ; vector of Rule, the default table
(mcc.rules/match-rule rules change)      ; => first matching Rule, or nil
(mcc.rules/condition-holds? condition change) ; => boolean; nil condition => true

;; src/mcc/classify.clj
mcc.classify/severity                    ; {:minor 0 :notify 1 :reassess 2}
(mcc.classify/max-class classes)         ; => class; empty => :minor
(mcc.classify/classify-changes rules changes) ; => Classification result
;; classify does NOT require mcc.diff. Only mcc.main composes the two:
;;   (classify-changes rules (diff-cards old new))

;; src/mcc/spec.clj
(mcc.spec/valid-card? card)              ; => boolean
(mcc.spec/explain-card card)             ; => nil if valid, else a string

;; src/mcc/report.clj
(mcc.report/->text result)               ; => human-readable string
(mcc.report/->edn result)                ; => pr-str of the result

;; src/mcc/main.clj
(mcc.main/run args)    ; => {:exit 0..3 :out "text" :err "text"}  pure, no System/exit
(mcc.main/-main & args); thin wrapper: calls run, prints, then System/exit
                       ; usage: clojure -M:run old.edn new.edn
```

CLI behaviour (`run`): read both files with `clojure.edn`, validate both
with spec (print explanation to stderr and exit 3 on invalid), classify with
`mcc.rules/rules`, print `->text`. Exit codes: 0 `:minor`, 1 `:notify`,
2 `:reassess`, 3 invalid input. Invalid input includes a wrong argument count,
a missing file, unparsable EDN and a card that fails spec; each gives exit 3
and a one-line message in `:err`, never a stack trace. `-main` prints `:out`
to stdout, `:err` to `*err*`, flushes, then calls `System/exit`.