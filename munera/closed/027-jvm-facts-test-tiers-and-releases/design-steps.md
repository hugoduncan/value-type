# Design follow-ups

- [x] Define the smoke row's minimum pass conditions for load, define, policy
  and one AOT round trip. State which fallback policies it checks, the expected
  representation, and whether the AOT producer and consumer use the same JVM.
  State whether `:missing-value-predicate` must be asserted on the selected
  non-Valhalla JDK. This defines the evidence for the existing smoke tier;
  it does not add matrix coverage.
- [x] Clarify when a new EA build "enters" the facts file: candidate editing or
  acceptance of the tested change. Explain how the forced-JIT row selects the
  candidate before admission, while keeping the facts file as the single source
  of build facts. Keep the existing requirement to document the admission rule;
  this does not request a new enforcement system.
- [x] Select the README install form that release preparation maintains and
  define its expected state before the first release and after preparation.
  If preparation maintains Git coordinates, explain how the tag's exact SHA
  becomes available without embedding the preparation commit's own SHA in that
  commit. If it maintains a Clojars version, state that choice. Define the
  expected README result for the existing preparation test; do not publish a
  release as part of this task.
- [x] Align the referenced release architecture with the required preparation
  behavior. `ramora/release-workflow.md`, under "Ownership and boundaries",
  says preparation "commits only the changelog", but this task requires the
  README version update in the same preparation commit. Identify that ownership
  description as an artifact to update alongside the release guide. Keep the
  task's required README update; this is not a request to change scope or to
  change the install-form follow-up above.

- [ ] Define whether the forced-JIT row covers only the Scry parent or also
  the runtime suite's child JVMs. `test/runtime/value_type/test_support.clj`
  (`run-java`, used by `run-in-new-jvm`) passes only `--enable-preview`;
  parent compilation flags do not reach the AOT consumers in `core_test.clj`
  and `deftype_compat_test.clj` or the probe check in `non_nil_fields_test.clj`.
  The plan excludes compatibility children but does not define this runtime
  child boundary. State the selected scope and its verification in the plan:
  either pass and check the forced-JIT flags in these runtime children, or
  retain normal child execution and state that limit in the admission evidence.
  Keep compatibility controls unchanged; this does not request new matrix rows.
