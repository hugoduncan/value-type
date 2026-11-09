# Verification and release decisions

Scope: the JVM facts, the test matrix, output review and the consumer
install source. These decisions extend [D11](../decisions.md#d11-verification-verification)
and the [release workflow](../../release-workflow.md). Basis: residual
(stressor) analysis of commit `5073893f`, recorded in residues-skill
`examples/value-type/recommendations.md`.

## D21. Supported-JVM facts (data model)

- Subject: the facts about each tested JVM.
- Choice: one data file in the repository lists each tested JVM: role
  (baseline, Valhalla or ordinary smoke), build ID, download URL for each platform, SHA-256,
  required flags and test tier (D22). `release.yml`, the test harness and the
  build tests read it. Documents link to it and do not repeat build IDs. The
  project follows the current EA build: a new build replaces the old row.
- Rejected:
  - Build IDs in each document. EA3 is named in about 400 places.
  - An archive of tested JDKs. The project moves to each new EA build and
    does not keep old builds verifiable.
- Rationale: one change for each new JVM build.
- Commitment: firm.

## D22. Test tiers (verification)

- Subject: matrix cost and JIT-only defects.
- Choice:
  - Full matrix on the primary pair: the Java 8 baseline and the current EA
    build, with preview off and on.
  - Each further JVM gets a smoke row: load, define, policy and one AOT
    round trip.
  - The primary pair adds one row that runs the value and array suites with
    forced JIT compilation. A new EA build enters D21 only after this row
    passes.
  - D21 selects the rows.
- Limits: smoke rows give less evidence; documents say "smoke-tested" for
  those JVMs. The forced-JIT row finds only defects that a test exercises.
- Rejected: the full cross product for each JVM (cost grows with each row);
  normal and interpreter execution only (JIT-only defects stay hidden).
- Commitment: firm.

## D23. Disassembly snapshots (verification)

- Subject: review of emitted bytes.
- Choice: for each case of the `deftype` compatibility corpus, a committed
  text disassembly of the emitted classes, for value and identity output. A
  test compares the emitted output with the snapshot. An intended change
  updates the snapshot in the same commit.
- Rejected: behaviour tests only. A byte change that keeps the tested
  behaviour does not show in a review of the source diff.
- Rationale: each change of emitted bytes is a text diff in review. A new
  Clojure or JDK version shows its output change (D19).
- Qualities: improves reviewability. Cost: snapshots change with each
  intended transform change and each version update.
- Commitment: firm.

## D24. Install tested releases (deployment)

- Subject: the source that consumers install.
- Choice: consumers install a release tag (`:git/tag` with `:git/sha`) or the
  Clojars version, not a clone of `master`. Only the release workflow creates
  release tags, after all runtime suites pass. The README names the current
  release.
- Rejected: install from `master`. Quality CI on push does not run the
  runtime or fallback suites, so `master` can contain a change that only the
  release suites reject.
- Rationale: consumers get only code that passed all runtime suites.
- Commitment: firm.
