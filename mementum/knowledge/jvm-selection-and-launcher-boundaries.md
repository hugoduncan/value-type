---
title: Keep JVM selection and launcher evidence separate
status: active
category: testing
tags: [jvm, facts, provisioning, test-tiers, integration-tests]
related: [mementum/knowledge/testing-library-artifacts.md, mementum/knowledge/validating-retained-jvm-evidence.md, mementum/memories/valhalla-jdk-selection-java-cmd.md]
depends-on: []
---

# Keep JVM selection and launcher evidence separate

Source: task `027-jvm-facts-test-tiers-and-releases`, its design, plan,
steps and implementation record. The lessons apply when changing JVM pins,
provisioning or test launchers.

## Facts select configurations; tests establish support

`dev/jvm/jvms.edn` owns current roles, exact builds, archives, checksums,
profiles and tiers. Machine paths are not build facts. Select entries by
`:role`, not row position or a fixed EA version prefix. Regression tests must
accept reordered facts and a selected EA build with a changed prefix, while
rejecting stale executable builds. Keep capability expectations independent
of the launch data (`839b82eb`, `1b18c43b`).

Use one named flag profile per configuration. Do not append preview and
exports to every child: plain and export-only controls need their missing
prerequisites. Current installation guidance links the facts or runtime guide;
historical experiment results retain their exact build identities.

A facts edit proposes a candidate; it does not prove support. EA admission
requires a forced-JIT pass on that exact candidate configuration. A changed
configuration needs a new pass. Smoke results do not establish full-matrix
coverage.

## Archive integrity and cached build identity are different checks

New downloads must pass SHA-256 verification before extraction, then exact
runtime-build verification before installation. Cached provisioning checks the
executable and build; it does not download again or hash installed files.
Use a new provisioning root for fresh archive-integrity evidence (`975a9ab2`,
`6f0248c0`). An override must not bypass build verification.

Validate role, build and platform as single path components before archive
access or writes. Reject separators, dot components, qualified keywords and
drive syntax, but preserve valid build punctuation such as `+`. Also require
canonical destination containment: lexical checks alone miss existing symlinks
that lead outside the root. Test rejection before effects (`c8adedd5`).

## Prove the actual launch and discovery boundary

The forced-JIT row compiles the Scry parent, including AOT production, with
`-Xcomp -Xbatch -XX:-TieredCompilation`. Runtime children use normal,
preview-only execution. A parent pass does not prove forced-JIT AOT
consumption or child-probe coverage. Inspect actual JVM input arguments in
both processes; reject inherited global JVM option variables that could
change controls (`28fd2d1f`).

A command-vector test cannot prove the launcher uses that command. A runtime
test conditional on compilation flags cannot detect a launcher that drops
all such flags. Run the unchanged launcher in a temporary project with real
Scry. Require the complete parent configuration, a passing suite exit and a
nonzero failing-suite exit (`62e00a86`).

To prove discovery stays within `test/runtime`, also put an executable
out-of-tier fixture on the temporary classpath. Require runtime execution
and no out-of-tier execution. A project containing only runtime fixtures
cannot detect broadened discovery. Mutate discovery to `test/` and confirm
the regression test fails (`d6d04ac4`).
