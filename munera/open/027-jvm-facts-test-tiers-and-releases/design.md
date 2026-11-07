# JVM facts, test tiers and tested releases

## Intent

Make each new JVM build one data change, keep matrix cost bounded, find
JIT-only defects before a build is claimed, and give consumers only tested
code. Realize defvaluetype D21, D22 and D24.

## Problem and context

- JVM facts are repeated: `release.yml` names the URLs and SHA-256 values, the
  runtime guide names the builds, and EA3 is named in about 400 places.
- The matrix runs every cell for each configured JVM. Each new JVM multiplies
  cost.
- Tests run in normal and interpreter execution. A defect that appears only
  in JIT-compiled code is not found.
- The README installs from a clone of `master`. Quality CI on push does not
  run the runtime or fallback suites, so `master` can hold a change that only
  the release suites reject.

Source: residual (stressor) analysis, residues-skill
`examples/value-type/recommendations.md`. Design: defvaluetype D21, D22, D24
(`decisions/verification-and-release.md`), `ramora/release-workflow.md`.

## Concepts

- Facts file: one data file of tested JVMs: role, build ID, URL per
  platform, SHA-256, required flags, tier.
- Primary pair: the Java 8 baseline and the current EA build.
- Smoke row: load, define, policy and one AOT round trip on one JVM.
- Forced-JIT row: the value and array suites with forced JIT compilation.

## Scope

Include:

- The facts file. `release.yml`, the child-JVM harness, the build tests and
  the runtime guide read or link it. The project follows the current EA
  build; no archive of old builds.
- Tiers: the full matrix on the primary pair; the smoke row runner for other
  JVMs; one forced-JIT row on the primary EA build. Planning selects the JIT
  flags and records why.
- One smoke row for a current non-Valhalla JDK that has `java.lang.classfile`
  (for example Temurin 25), to exercise the smoke tier and the
  `:missing-value-predicate` path. Planning confirms the build.
- The rule: a new EA build enters the facts file only after the forced-JIT
  row passes. State it in the runtime guide and the release guide.
- README installation from a release tag (`:git/tag` and `:git/sha`) or the
  Clojars version. Release preparation updates the README version with the
  changelog. Until the first release exists, the README says so and marks the
  clone path as development only.
- Docs say "smoke-tested" for smoke-row JVMs.

Exclude:

- A JDK archive; a second distribution channel; release signing (decided no).
- Running the runtime suites on each push.
- Publication of a first release.

## Contract

- Changing the EA build is one facts-file change plus the format row of
  task 023 if the format changes.
- `release.yml` fails clearly when a download does not match the facts-file
  SHA-256.
- The release workflow still creates tags only after all suites pass.

## Architecture

- The facts file is data. A small reader gives the CI workflow and the
  harness their JVM paths and flags; the YAML holds no build facts.
- Tier selection reads the facts file.

Rejected: build facts in the workflow YAML; full matrix for each JVM.

## Qualities and risks

- Better: maintainability, bounded cost, earlier JIT defect detection.
- Cost: smoke rows give less evidence. The forced-JIT row is slow and finds
  only defects that a test exercises.
- Risk: hosted Actions execution stays unverified (release workflow limits).

## Acceptance

1. No workflow, test or guide repeats a JVM URL or SHA-256 outside the facts
   file. The runtime guide links it.
2. The release workflow, in preview mode locally emulated as today, selects
   JVMs from the facts file and checks their SHA-256.
3. The smoke runner passes on the smoke-row JDK; the forced-JIT row passes on
   EA3. Commands and durations are in `implementation.md`.
4. Release preparation tests show the README version update in the
   preparation commit.
5. README, runtime guide, release guide, `CHANGELOG.md`, the embodiment gap
   table (D21, D22, D24) and `mementum/state.md` are updated. All suites and
   checks pass.

Create `plan.md`, `steps.md` and `implementation.md` before execution.
