# model-change-classifier

When software that makes decisions about patients changes, someone has to decide how much review the change needs. Re-checking everything on every update makes release cycles slow. Skipping checks makes them risky. A version label bump and a new training dataset are clearly not the same kind of change, but both show up in the same diff.

This is a small, data-driven sketch of that triage. It takes two versions of a device description (a "model card" written as EDN), diffs them, matches each change against a table of rules, and classifies each one as `:minor`, `:notify` or `:reassess`. The overall verdict is the worst class found.

> **Disclaimer:** the rules and thresholds in this repository are illustrative only. They are not real regulatory logic. Nothing here is regulatory advice, and nothing here is a substitute for assessing a real device.

## Quick start

You need the [Clojure CLI](https://clojure.org/guides/install_clojure) and Java 21 (any recent JDK should work).

```
clojure -M:run examples/base.edn examples/reassess.edn; echo "exit=$?"
```

Output:

```
Verdict: REASSESS

REASSESS  performance.sensitivity
  kind:   changed
  change: 0.92 -> 0.8
  rule:   :performance-drop-large
  why:    Large drop in a performance metric.
exit=2
```

The exit code is the verdict, so it is easy to use in scripts:

| Exit | Meaning                                                  |
|------|----------------------------------------------------------|
| 0    | `:minor` (including no changes at all)                   |
| 1    | `:notify`                                                |
| 2    | `:reassess`                                              |
| 3    | invalid input (bad arguments, missing file, bad EDN, card fails the spec) |

## How it works

**1. Input.** Each file is one EDN map. Here is `examples/base.edn`:

```clojure
{:name "ExampleDR"
 :version "1.0.0"
 :intended-use "Screening for diabetic retinopathy in adults"
 :model {:architecture "resnet50"
         :training-data-hash "a1b2c3"
         :input-types [:fundus-photo]
         :notes "baseline"}
 :performance {:sensitivity 0.92
               :specificity 0.88}
 :deployment {:platforms #{:cloud}}}
```

**2. Diff.** The two cards are walked recursively. Each difference becomes a change such as `{:path [:performance :sensitivity] :kind :changed :before 0.92 :after 0.8}`, where `:kind` is `:added`, `:removed` or `:changed`. The result is sorted so output is deterministic.

**3. Rules.** Each change is matched against an ordered table of plain EDN rules. This is one of them, copied from `src/mcc/rules.clj`:

```clojure
{:id :performance-drop-large
 :match [:performance :*]
 :when [:drops-by-more-than 0.05]
 :class :reassess
 :rationale "Large drop in a performance metric."}
```

`:match` is a path pattern, `:when` is an optional numeric condition, and `:class` is the result.

**4. Verdict.** The overall verdict is the most severe class among all changes (`:minor` < `:notify` < `:reassess`). No changes means `:minor`.

Example pairs, each compared against `base.edn` (details in [examples/README.md](examples/README.md)):

| File                | What changed                              | Exit |
|---------------------|-------------------------------------------|------|
| `minor.edn`         | version 1.0.0 to 1.0.1                    | 0    |
| `notify.edn`        | deployment platform `:edge` added         | 1    |
| `reassess.edn`      | sensitivity dropped from 0.92 to 0.80     | 2    |
| `unknown-field.edn` | extra top-level key that no rule covers   | 2    |
| `invalid.edn`       | sensitivity of 1.5 fails the card spec    | 3    |

**Code layout** (`src/mcc/`):

- `diff.clj`: compares two cards and returns a sorted vector of changes.
- `spec.clj`: `clojure.spec` definitions and helpers that check a card has the expected shape.
- `rules.clj`: the default rule table, path matching and condition checks.
- `classify.clj`: applies the rules to each change and computes the overall verdict.
- `report.clj`: renders a result as human-readable text or as EDN.
- `main.clj`: the CLI. A pure `run` function plus a thin `-main` that prints and exits.

## Design choices and limitations

- Input is EDN only. There is no JSON support, which keeps the tool dependency-free apart from Clojure itself.
- Vectors and sets are compared as whole values. Adding one element to `:platforms` is one `:changed` at that path, not a per-element diff.
- The diff is a small hand-written recursive walk. It is not `clojure.data/diff`, which returns three nested partial structures instead of a flat list of changes, and compares vectors by position (a vector that gains an element shows up as `[nil :oct-scan]`).
- A map that is added or removed under a key is reported as one change at that key, not one change per entry.
- Performance-metric rules assume higher is better. A metric where lower is better (an error rate, say) would need its own rules.
- The `:*` wildcard matches exactly one path segment, and a pattern must be the same length as the path. There are no other wildcards.
- Rules are tried in order and the first match wins, so specific rules go before general ones.
- A change that matches no rule is escalated to `:reassess` on purpose, so nothing passes silently. That includes added, removed or non-numeric changes under `[:performance :*]`, because the numeric conditions only apply to numbers that changed. It also covers `1` versus `1.0`, which are not `=`.
- The card spec only checks the fields it lists and allows extra keys. Extra keys are how "unknown field" changes arise.
- The thresholds are arbitrary example values (see the disclaimer above).

## Adding a rule

Rules are just entries in the `rules` vector in `src/mcc/rules.clj`. Say a change to the `:preprocessing` note should be treated as a quiet edit instead of a heads-up. Edit the existing entry, or add a more specific one before it:

```clojure
{:id :preprocessing-change
 :match [:model :preprocessing]
 :class :minor
 :rationale "Preprocessing description edited, no behaviour change."}
```

Order matters: put the new rule above any broader rule that would also match the same path, because the first match wins. To test a change by hand, copy an example card, edit it, and run `clojure -M:run examples/base.edn your-card.edn`.

## Running the tests

```
clojure -M:test
```

This runs the whole suite and exits 0 when everything passes. A GitHub Actions workflow (`.github/workflows/test.yml`) is set up to run the same command on every push and pull request.

## Author's note

<!-- TODO(author): write a short, honest note in your own words about why you built this and what you learned. Left blank on purpose. -->

## Data shapes

See [docs/CONTRACT.md](docs/CONTRACT.md) for the exact shapes of cards, changes, rules and results, and the function signatures.
