# JVM facts, test tiers and tested releases

## Intent

Centralize JVM facts, bound test cost, detect JIT-only defects and install
tested releases. Realize defvaluetype D21, D22 and D24.

## Problem and context

Repeated JVM facts increase maintenance cost. Normal and interpreter runs
can miss JIT defects. README installation from `master` bypasses release
testing; push CI omits runtime and fallback suites.

Source: residual analysis, residues-skill `examples/value-type/recommendations.md`.
Design: defvaluetype D21/D22/D24 in `decisions/verification-and-release.md`
and `ramora/release-workflow.md`.

## Concepts

- Facts file: JVM role, build ID, platform URLs, SHA-256, required flags, tier.
- Primary pair: Java 8 baseline and current EA.
- Smoke: load, define, policy, one AOT round trip.
- Forced-JIT: value and array suites with forced compilation.

## Scope

Include:

- The facts file. `release.yml`, the child-JVM harness, the build tests and
  the runtime guide read or link it. The project follows the current EA
  build.
- Tiers: the full matrix on the primary pair; the smoke row runner for other
  JVMs; one forced-JIT row on the primary EA build. Planning selects the JIT
  flags and records why.
- One smoke row for a current non-Valhalla JDK that has `java.lang.classfile`
  (for example Temurin 25), to exercise the smoke tier and the
  `:missing-value-predicate` path. Planning confirms the build.
- The rule: a new EA build is admitted as tested only after the forced-JIT
  row passes. Document admission in the runtime and release guides.
- README installation from the Clojars version. Release preparation updates
  the README version with the changelog in one commit, subject to the
  publication condition below.
- Docs say "smoke-tested" for smoke-row JVMs.

Exclude:

- A JDK archive; a second distribution channel; release signing (decided no).
- Running the runtime suites on each push.
- Publication of a first release.
- Changing the hosted release runner from Linux x64, or requiring x64 CPU
  emulation on the ARM64 development host.

## Contract

- Changing the EA build is one facts-file change plus the format row of
  task 023 if the format changes.
- `release.yml` fails clearly when a download does not match the facts-file
  SHA-256.
- The release workflow still creates tags only after all suites pass.
- Update `ramora/release-workflow.md`, "Ownership and boundaries", alongside
  the release guide: preparation owns the README version and changelog in
  one commit, not "only the changelog". Publication remains separate.

### Local Linux verification

Use Linux ARM64 in an ARM64 VM on the development host for local release
preview checks. Preserve the exact baseline, Valhalla and ordinary build pins;
publishers provide Linux ARM64 archives for all three builds. Add checked platform facts
and executable selection before running the gates. Do not substitute builds.

Run the complete local preview gate sequence, notes selection and JAR build
without credentials or publication writes. Keep finite bounds and check for
surviving descendant processes. Do not skip tests or weaken assertions to fit
the platform. No further x64-emulated full-matrix run is required for acceptance.

Keep Linux x64 facts and the existing hosted runner. Local ARM64 results prove
only that platform; they do not establish x64, hosted Actions or publication
success. Retain previous x64 failures and timeouts as historical evidence,
without requiring their diagnosis to complete this task. Failures reproduced
on the selected local platform must still be resolved.

### README release coordinates

Maintain `org.hugoduncan/value-type {:mvn/version "VERSION"}`, not Git
coordinates. VERSION equals the stamped changelog version and is known before
the preparation commit; no self-referential SHA is needed.

- Before first preparation, state that no release is published. Give no
  invented version. Label cloning and `:local/root` as development only.
- Preparation installs or updates the Clojars example and consistent status
  text: these are prepared coordinates, usable only after this version is
  published by the release workflow. Do not claim tests passed or publication
  occurred, including at first preparation.
- After publication, the same coordinates identify the tested artifact; no
  second README commit is needed. Clone instructions stay development only.

Local preparation tests cover first preparation and version replacement.
Read the committed README: its version must equal the stamped and returned
version, with the publication condition and development-only clone label.
Successful preparation makes exactly one commit containing only `README.md`
and `CHANGELOG.md`, leaves a clean worktree, and changes no tags or remotes.

### EA admission

Replace the primary EA entry in a proposed facts-file change. The forced-JIT
runner selects that revision's primary EA entry, including build, checksums
and flags, not the previous build or a second source. This is an unaccepted
candidate, not a tested-build claim.

Accept only after the forced-JIT value and array suites pass on that exact
candidate configuration. Record command, build identity and result in the
change's implementation evidence. Configuration changes require a new pass.
Failed candidates remain unaccepted; the accepted revision retains the prior
build. No archive, candidate registry or automated admission gate is required.
Full-matrix and release checks still apply.

### Smoke pass conditions

Run the selected non-Valhalla JDK normally, without preview or internal
exports, with `value-type.value-classes=auto`. Do not cross JVMs or modes.

- Load: require `value-type.core`, `value-type.array` and
  `value-type.capability` without error or fallback warning.
- Capability: `report` must return `:available? false` and
  `:reason :missing-value-predicate` for `:value-class`. The selected JDK
  must have `java.lang.classfile` but no `Class.isValue`.
- Define and policy: use separate fresh processes for explicit `silent`,
  `warn` and `error`. Define two ordinary `defvaluetype` types. With `silent`
  and `warn`, both are identity classes: construction and field reads work,
  and two equal-field instances remain distinct. `silent` gives no fallback
  warning; `warn` gives exactly one across both definitions. With `error`,
  each definition fails with a root `java.lang.Error` fallback diagnostic;
  its class stays undefined. Loading succeeds under all three policies.
  Invalid policies and the unset default stay in the full matrix.
- AOT: compile one ordinary `defvaluetype` namespace under `silent`, then
  load its compiled output without fixture source in a separate fresh
  process, using the same JDK, flags and `silent` policy. Both exit
  successfully without fallback warnings. The consumer checks construction,
  field values and distinct identity.

## Architecture and risks

A small facts-file reader supplies CI and harness JVM paths, flags and tiers.
Reject build facts in YAML and a full matrix for every JVM. Smoke gives less
evidence. Forced JIT is slow and finds only exercised defects. Hosted Actions
execution remains unverified.

## Acceptance

1. No workflow, test or guide repeats a JVM URL or SHA-256 outside the facts
   file. The runtime guide links it.
2. The release preview gate sequence runs locally on Linux ARM64 with
   facts-selected JVMs, checked SHA-256 values and exact build identities.
   Record platform, commands, durations and results for every gate, notes
   selection and JAR build. Keep x64 CI unchanged and report its separate
   verification limit; local ARM64 success is not x64 evidence.
3. The smoke runner meets the smoke pass conditions on the selected JDK;
   the forced-JIT row passes on EA3. Record commands and durations in
   `implementation.md`.
4. Release preparation tests meet the README release-coordinate contract,
   including first preparation and replacement of a prior version in the
   same commit as the changelog.
5. README, runtime guide, release guide, `ramora/release-workflow.md`,
   `CHANGELOG.md`, the embodiment gap table (D21, D22, D24) and
   `mementum/state.md` are updated. All suites and checks pass.

Create `plan.md`, `steps.md` and `implementation.md` before execution.
