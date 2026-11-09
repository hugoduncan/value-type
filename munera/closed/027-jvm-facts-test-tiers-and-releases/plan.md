# Implementation plan

## Approach

Implement D21, D22 and D24 without changing library runtime behavior. Keep
publication separate from preparation. Do not prepare or publish this checkout.
Use the existing `implementation.md` for dated decisions, commands, durations
and acceptance evidence. Commit each completed slice.

### JVM facts and ownership

Add `dev/jvm/jvms.edn` and a small Java-8-safe reader under
`dev/jvm/value_type/`. Share this development-only path between Babashka,
compatibility, smoke and build tooling. Do not add it to library paths or JARs.
The reader returns data; provisioning and process execution remain separate.

Use stable role keys: `:baseline`, `:valhalla`, and `:ordinary`. Each entry has
an exact build identity, tier, platform archive URL and SHA-256, executable
layout, and named flag profiles. Support Linux x64 for Actions, Linux ARM64
for local release preview in an ARM64 VM, and macOS ARM64 for local checks.
The existing Temurin 8 executable on macOS is x64 and needs Rosetta; record
its actual archive architecture rather than invent an ARM64 baseline asset.
Use native ARM64 EA and smoke archives on macOS. Use native ARM64 archives
for all three roles on Linux ARM64; no CPU emulation is needed there.
Preserve the existing Java 8 and EA3 pins. Resolve
local executables from explicit role environment overrides, then a documented
provisioning directory; reject missing or mismatched builds clearly. Preserve
`VALUE_TYPE_BASELINE_JAVA` and `VALUE_TYPE_VALHALLA_JAVA`; add
`VALUE_TYPE_SMOKE_JAVA`. Machine paths are not build facts.

Keep preview-off, preview-on, export-only and preview-with-export profiles
separate. Never append all required flags to every child. Keep test expectations
independent of selected launch data. Refactor `jvm-config`, build consumers and
runtime environment diagnostics to use role data rather than build literals.
Preserve Java-8 loading, child isolation, limits and source-free/JAR-only checks.

Provision through a Babashka command that reads the facts, downloads archives,
checks SHA-256 before extraction and reports role/platform-specific errors.
Use the same command locally and in `release.yml`; test mismatch rejection with
real temporary archives. Maintained guides link the facts instead of repeating
build IDs or download locations. Historical Munera and experiment evidence is
not a second source for launch selection and is not rewritten.

### Local Linux platform

Add `:linux-aarch64` facts for all three existing builds using publisher URLs
and SHA-256 values. Extend platform detection for Linux `aarch64` and `arm64`.
Test selection, archive architecture, checked provisioning and exact-build
validation without changing the existing x64 or macOS mappings.

Use a separate Linux ARM64 container in an ARM64 VM (`linux/arm64`), a
full-history checkout, pinned tooling and a clean provisioning root. Verify the
guest and all JVM architectures as well as the build identities. Run the full
preview gate sequence, notes selection and JAR build without credentials or
publication writes. Preserve finite limits, structured results and descendant
cleanup. Measure duration on ARM64; do not reuse x64-emulation cost estimates.

Keep `release.yml` on Linux x64. Its explicit platform selectors remain x64;
the local run selects ARM64 facts with the same roles, profiles and gates.
This is local ARM64 workflow-equivalent verification, not a hosted or x64
workflow pass. Preserve previous x64 diagnostic evidence, but stop scheduling
x64-emulated full-matrix runs. Their unresolved timeouts are an evidence limit,
not an acceptance dependency. Resolve any failures on the selected ARM64 path.

### Test tiers and selected configurations

Keep the full matrix on the primary pair only: runtime on preview-enabled
Valhalla with exports; fallback on Java 8 and Valhalla preview off/on; cross-JVM
once with the Java 8 parent; existing normal/interpreter child axes unchanged.

Select Temurin HotSpot JDK `25.0.5+8` for the ordinary smoke role. Adoptium's
latest-25 API returned this exact release and platform checksums for Linux x64
and macOS ARM64 during planning on 2026-11-08. Pin those assets, not a latest
URL. Metadata confirms availability, not smoke success. Verify the downloaded
JVM has `java.lang.classfile` and lacks `Class.isValue` before recording support.

Add `test/smoke/` and `:smoke-test`, sharing baseline-safe child and AOT support
without discovering compatibility suites. Run only the design's load,
capability, three fresh-process policy cases and same-JDK source-free AOT round
trip. Use normal execution, no preview/exports and explicit `auto`. Do not add
this JVM to cross-JVM receivers or normal/interpreter products.

Add `bb test:forced-jit` to select the facts revision's primary Valhalla role
and run all `test/runtime/` through Scry. This is the existing value and array
suite boundary, including implementation tests. Use preview, exports, explicit
`auto`, and `-Xcomp -Xbatch -XX:-TieredCompilation`: compile on first invocation,
wait for compilation, and use the optimizing compiler rather than stop at a
lower tier. EA3 accepted these flags in a planning-time `java -version` check;
this was not a suite pass. Forced compilation covers only the Scry parent,
including AOT production there. Runtime children keep normal compilation and
the existing preview-only launch through `test/runtime/value_type/test_support.clj`.
This excludes forced-JIT coverage of AOT consumption in `core_test.clj` and
`deftype_compat_test.clj`, and the fresh-process probe in `non_nil_fields_test.clj`.
This bounded row avoids changing child behavior or adding matrix rows.
Compatibility controls remain unchanged.

Verify the boundary with real-process runner tests: inspect JVM input arguments
in the Scry parent and a child launched through `run-in-new-jvm`. Assert the
three compilation flags in the parent and their absence in the child, with
preview retained. The forced-JIT runner rejects nonempty `JAVA_TOOL_OPTIONS`,
`JDK_JAVA_OPTIONS` or `_JAVA_OPTIONS`; do not use global options to pass flags.
Report executable, build, parent/child flags and elapsed time. Admission evidence
and runtime/release guides must state parent-only forced compilation; a row pass is not forced-JIT
child evidence.

A proposed EA facts edit is a candidate. The runner must use that entry without
an old-build default or separate candidate registry. Accept it only after this
exact configuration passes. Configuration changes require another pass; failed
candidates do not replace the accepted revision. Full release gates still apply.

### Release preparation

Give README status and installation text small, uniquely marked maintained
regions. Before preparation, state that no release is published and label clone
and local-root use as development only. Do not invent coordinates.

Preparation validates both regions and the changelog before writing either.
Compute the next commit-count version once; stamp the changelog and render
`org.hugoduncan/value-type {:mvn/version "VERSION"}` with the publication
condition. Stage and commit only these two files in one commit. Keep the
post-commit count guard, clean-worktree guards and inspectable partial failures.
Read committed README bytes in real-Git first/replacement tests. Never claim
preparation passed release tests or published an artifact.

## Slice order

1. Facts, reader and checked provisioning, with narrow tests. The first pass
   supplies validated facts and profile selection; executable resolution is now
   available. Checked provisioning now uses the shared role/build/platform
   installation directory; consumer migration is next.
2. Harness/build migration and unchanged primary-matrix verification.
   Compatibility and build consumers, diagnostics and focused verification are
   complete. Full matrix verification remains in the acceptance slice.
3. Smoke runner and forced-JIT runner, with real JVM evidence. Smoke discovery
   and startup checks now pass on the checked ordinary build. Definition-policy
   cases and source-free same-JDK AOT now pass in fresh processes. The bounded
   smoke contract is verified; the forced-JIT launcher is implemented with focused configuration tests. Real Scry parent/child boundary checks, failure propagation and the full forced-JIT run now pass. Release preparation is next.
4. README maintenance and release preparation contracts. Marked regions and
   the pure, validated renderer are complete with focused tests. Preparation integration and real-Git two-file contracts now pass, including
   validation and hook failures. Workflow integration is next.
5. Release workflow integration and maintained documentation. Checked role
   provisioning and JVM-tooling/smoke/forced-JIT gates are integrated. Runtime and preview-enabled fallback launch flags now use facts profiles;
   runtime/release guides now describe selection, tiers, admission and preparation.
   Architecture ownership, D21 role wording, Unreleased notes and embodiment
   status now match completed work. Full acceptance follows.
6. Full checks, Linux ARM64 local preview and acceptance evidence. The local
   macOS suite sequence and staged Linux x64 gates have recorded results.
   The x64-emulated cross-JVM gate has no complete passing summary; retain its
   timeouts and diagnostics without scheduling another x64-emulated run.
   Linux ARM64 facts and selection now pass focused tooling tests, including
   Java 8. All three roles are now provisioned and build/architecture verified in
   `vt-task027-arm64`; Java-8 JVM-tooling tests pass (Slice 6z). The remaining local preview gates are complete. Quality, tooling, JVM-tooling
   and smoke now pass (Slice 6aa). Forced JIT and runtime also pass (Slice 6ab).
   The three fallback parents and cross-JVM gate now pass (Slice 6ac).
   Build/release and preview notes/JAR now pass (Slice 6ad), completing the
   staged native ARM64 preview sequence. Retain structured results, commands,
   durations and process cleanup evidence. Affected quality checks and the maintained-source duplication audit pass.
   Slice 6ae maps all five acceptance criteria to evidence, keeping local
   ARM64, earlier x64 and hosted limits separate.

## Risks

- Forced compilation can expose JVM defects and exceed current time limits.
  Measure first; adjust finite limits with evidence, not skipped tests.
- Download availability, platform layouts and override identity can differ.
  Fail clearly; do not silently substitute a build or skip a tier.
- New shared paths can leak into isolated consumers or artifact contents.
- Preparation hooks can add paths or change history. Check the final commit
  against the original HEAD: exactly the two document paths and one unchanged
  parent. Retain count, clean-state and document checks with real-Git tests.
- Smoke evidence is narrower than full-matrix evidence. Hosted Actions,
  credentials and live publication remain unverified.

## Review notes

Put facts/provisioning tests in `test/jvm/` with `:jvm-test`; include them in
quality discovery, final checks and release gates. Provision into
`~/.cache/value-type/jvms/` locally and `RUNNER_TEMP/jvms/` in Actions, outside
the build's destructive `target/`. Set all role overrides to verified paths.


## Test review follow-ups

Run the unchanged Babashka launcher in a temporary project with real Scry,
selected JVM facts and a small runtime fixture. Check observed parent arguments
and both passing and failing suite exits. Keep the normal runtime row unchanged.
Then select parent-role expectations by role and test reordered facts.

Run launcher tests through a test-only Babashka supervisor so Java 8 does not
load ProcessHandle. Bound execution at 180 seconds and tree cleanup at 15
seconds. Stop children before parents, await termination, and verify recorded
handles before deleting the temporary project. A child readiness file triggers
the timeout path without a 180-second test delay. Retain the project and logs
on cleanup errors; assertion diagnostics remain in Scry failure output; production launcher behavior is unchanged.

Keep an executable out-of-tier fixture on the isolated project classpath.
Check its execution marker is absent in each launcher case and the runtime
observation is present. Verify the check rejects discovery broadened to `test/`.

## Code-shaper review follow-up

Validate role, build and platform as single path components in facts and at
installation-path construction. Preserve build punctuation such as `+`.
Require canonical destination containment, including existing symlinks. Reject
invalid paths before archive access, downloads, extraction or installation writes.

Bound executable verification at 30 seconds by default, with an explicit timeout
argument for callers. Capture output in temporary files so a full pipe cannot
block startup checks. On timeout, force termination and await it for at most
five seconds, with one final five-second cleanup attempt if still alive;
include selection context, output and termination status in the
error. Use Java 8 process APIs. Test a real stalled executable and check its PID
is no longer alive, while retaining successful and mismatched-build checks.
