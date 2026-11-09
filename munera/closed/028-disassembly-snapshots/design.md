# Disassembly snapshots

## Closure

Closed as won't do at the user's request on 2026-11-08. Acceptance criteria
were not completed; D23 remains unimplemented.

## Intent

Make each change of emitted class bytes a reviewable text diff. Realize
defvaluetype D23.

## Problem and context

The transform works on bytes. A review of the source diff does not show how
the emitted classes change, and the tests check behaviour, not bytes. A byte
change that keeps the tested behaviour passes review and tests. A new Clojure
or JDK version can change the output with no visible diff.

This task comes after tasks 023 and 026, which change emitted output, so the
first snapshots record the final shape.

Source: residual (stressor) analysis, residues-skill
`examples/value-type/recommendations.md`. Design: defvaluetype D23
(`decisions/verification-and-release.md`).

## Concepts

- Snapshot: a committed, normalized text disassembly of the classes that one
  corpus case emits, for value and identity output.
- Normalization: removal of output that changes between runs, for example
  generated function class numbers and probe UUIDs.

## Scope

Include:

- Snapshots for each case of the `deftype` compatibility corpus
  (`value-type.deftype-compat-test`), value output on EA3 and identity
  output.
- A test that compares emitted output with the snapshots.
- A command that writes new snapshots. An intended change updates them in
  the same commit.
- Normalization rules, documented with the snapshots.

Exclude:

- Snapshots of `defvaluetype*` closed-record cases and non-nil field cases;
  a later task can add them.
- Byte-level comparison.
- Snapshots on JVMs other than the primary pair.

## Contract

- The comparison test fails with a readable diff when emitted output differs
  from a snapshot.
- Two runs on the same JVM and Clojure version give equal normalized output.

## Architecture

- Disassembly uses the JDK class-file API printer on EA3, or `javap`;
  planning selects one and records why. Identity output uses the same printer
  on a JVM that has it.
- The snapshot test runs in the runtime suite on the primary EA build.

Rejected: behaviour tests only; unnormalized snapshots (unstable).

## Qualities and risks

- Better: reviewability of byte changes and version updates.
- Cost: snapshot updates for each intended transform change and each Clojure
  or JDK update.
- Risk: incomplete normalization gives unstable tests. Run the test twice in
  fresh JVMs to check stability.

## Acceptance

1. Snapshots exist for every corpus case, value and identity output.
2. The comparison test passes, and passes again in a second fresh run.
3. A deliberate one-flag change in the transform makes the test fail with a
   diff that names the flag (shown in `implementation.md`, then reverted).
4. The development guide documents the update command and normalization.
   `CHANGELOG.md`, the embodiment gap table (D23) and `mementum/state.md` are
   updated. All suites and checks pass.

Create `plan.md`, `steps.md` and `implementation.md` before execution.
