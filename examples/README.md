# Examples

All cards describe the fictional device "ExampleDR". Each file is a complete
card. Run `base.edn` (old) against one variant (new), from the repo root:

    clojure -M:run examples/base.edn examples/reassess.edn

| File               | What changed versus base.edn                          | Exit |
|--------------------|-------------------------------------------------------|------|
| `base.edn`         | Nothing (compared with itself)                        | 0    |
| `minor.edn`        | Version bumped from 1.0.0 to 1.0.1                    | 0    |
| `notify.edn`       | Deployment platform `:edge` added                     | 1    |
| `reassess.edn`     | Sensitivity dropped from 0.92 to 0.80                 | 2    |
| `unknown-field.edn`| Extra top-level key `:regulatory-note` (no rule)      | 2    |
| `invalid.edn`      | Sensitivity is 1.5, which fails the card spec         | 3    |

Exit codes: 0 minor, 1 notify, 2 reassess, 3 invalid input.
