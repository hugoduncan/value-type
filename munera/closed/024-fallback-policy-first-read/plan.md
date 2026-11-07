# Plan: fallback policy at first read

## Approach

Move the policy read from namespace load to first use through a delay in
`impl.runtime`, then expose the fixed value through `report`.

Key decisions (from design.md Architecture):

- Replace the load-time `(def ^:private policy ...)` in `impl.runtime` with
  a private delay plus a public zero-arity accessor
  `impl.runtime/fallback-policy`. The delay reads `value-type.fallback`
  (default `silent`), validates it, and returns a keyword (`:silent`,
  `:warn`, `:error`). An invalid value makes the delay throw; delay
  semantics preserve the exception for each later read.
- `enforce-fallback!` switches its `case` to the keyword values and reads
  only the accessor. Only the docstrings of `enforce-fallback!` and
  `enforce-fixed-fallback!` contain "initialized policy"; it becomes
  "policy in effect". The `guarded-deftype` and `guarded-deftype*`
  docstrings ("Enforce fixed fallback policy") stay unchanged. Behaviour,
  call sites and messages of all four are unchanged.
- `value-type.capability/report` requires `impl.runtime`, reads the
  accessor first (before any probe), and puts the value unchanged under
  `:fallback-policy`. No policy logic lives in `value-type.capability`.
- Tests replace the accessor with `with-redefs`, matching the D14
  capability-accessor pattern.
- Test locations:
  - Acceptance 1, 2 and 4 and the later-read checks: a new cross-JVM test
    `value-type.fallback-policy-test`
    (`test/cross-jvm/value_type/fallback_policy_test.clj`) with child
    checks in `value-type.fallback-policy-checks`
    (`test/compatibility/value_type/fallback_policy_checks.clj`), in the
    pattern of `capability_matrix_test` and `capability_matrix_checks`. It
    uses the explicit runtimes of `config/incompatible-receivers` (Java 8,
    preview off) and `config/receivers` (also preview on) from one host.
  - `value-type.runtime-test` (compatibility suite, `:selected` host only)
    keeps the policy tests whose result does not depend on value-class
    support: `invalid-policy-test`, `policy-test` and
    `invalid-policy-report-test`.
- `api_docs_test` checks `value-type.api-docs/generated-markdown`
  (`bb/value_type/api_docs.clj`, quickdoc over the source docstrings), not
  `doc/API.md`. Its `report` strings change in
  Slice 3, after the Slice 2 docstring. Its `defvaluetype` strings change
  in Slice 4, after the Slice 4 docstring. `doc/API.md` regeneration is
  for `bb api-docs --check`.

Slice order:

1. Runtime policy owner: delay + accessor + keyword `case`, docstring
   updates in `impl.runtime`.
2. Report: `:fallback-policy` in `report`, policy read before probes,
   `report` docstring rewrite.
3. Tests: rewrite the tests that assert the old contract or load-time
   validation; add the cross-JVM tests of acceptance 1, 2 and 4 and the
   later reads; capability-matrix coverage per acceptance 3.
4. Docs and design sources: docstrings, `doc/API.md`, the `api_docs_test`
   `defvaluetype` strings, usage guide (fallback and capability report
   sections), README, CHANGELOG, the runtime guide
   (`doc/development/runtime.md`: focused command and coverage of
   `value-type.fallback-policy-test`, report-matrix text, isolation rule 5),
   embodiment gap table (remove D10, D14), `embodiment/runtime.md`,
   assertion A-34, `mementum/state.md`.
5. Verification: full suites plus `bb clj-fmt:check`, `bb clj-kondo:lint`,
   `bb vale`, `bb api-docs --check`.

## Risks

- These tests assert the old four-key `report` contract and must change:
  - From design.md Qualities and risks:
    `runtime-test/invalid-policy-report-test`,
    `capability-test/report-translation-test`, `capability_matrix_checks`
    `owner-report`, and `api_docs_test` (the "does not read the
    `value-type.fallback` policy" string).
  - Also exact report maps: `controlled_value_capability_test` (`:report`,
    launch policy `silent`) and `build_test/isolated-consumer-test`
    (`consumer-reports`, `-Dvalue-type.fallback=silent`).
- These tests rely on load-time validation and must move to first-read
  validation:
  - `runtime-test/invalid-policy-test`: expects the throw at
    `(require 'value-type.impl.runtime)`; the require now succeeds.
  - `runtime-test/policy-test`: sets `"invalid-later"` after the require
    but before the first read; that value would now be the first read.
  The delay keeps state per JVM, so first-read tests need child JVMs or
  `with-redefs` isolation.
- The compiler wraps the first-read exception in `Compiler$CompilerException`
  during macroexpansion; invalid-policy definition tests must assert the
  cause chain, not the top-level type.
- Acceptance 1, 2 and 4 run in the cross-JVM suite
  (`clojure -M:cross-jvm-test`). It needs the Java 8 and Valhalla 27 EA
  executables of `jvm_config/configuration` (`VALUE_TYPE_BASELINE_JAVA`,
  `VALUE_TYPE_VALHALLA_JAVA` or the default paths). `runtime-test` covers
  only the `:selected` host.
- Many documentation sites describe load-time policy or the four-key
  report; missing one leaves stale prose. The checklist is the Acceptance 6
  list plus these sites that it does not name:
  - `doc/usage.md` "Capability report": "`report` does not read the
    `value-type.fallback` policy" (l.356), the intro, the example result
    and the key table.
  - `doc/development/runtime.md`: no focused command for
    `value-type.fallback-policy-test`, the report-matrix text "Each report
    must equal the expected row", and isolation rule 5 without the first
    policy read.
