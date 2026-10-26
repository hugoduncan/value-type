---
title: Verify library artifacts without checkout dependencies
status: active
category: testing
tags: [packaging, tools.build, classpath, integration-tests, fixtures]
related: [mementum/knowledge/testing-clojure-generated-classes.md, mementum/memories/valhalla-jdk-selection-java-cmd.md]
depends-on: []
---

# Verify library artifacts without checkout dependencies

Source: task `008-jar-build`, its design, plan, steps and implementation
notes. Evidence: consumer check `9ffcd42`, CLI review fix `a8750a9`,
and input-preservation review fix `68cd858`. These checks passed on
Valhalla JDK `27-jep401ea3+1-1`. Reusable examples are in
`test-build/build_test.clj`.

## A new JVM does not ensure an isolated consumer

`value-type.test-support/run-in-new-jvm` appends the test JVM classpath.
That is useful for consumer AOT tests, but it includes checkout sources.
An artifact test with that classpath can pass even when the JAR lacks
required sources or dependency metadata. The build test helper
`build-process` also inherits a test classpath; do not use it to prove
consumer isolation.

For artifact verification:

- Read runtime dependency coordinates from the generated POM, not the
  checkout's dependency map. Resolve them with a separate deps map that
  has `:paths []`, and a basis with `:root nil`, `:user nil` and
  `:aliases []`.
- Start Java with only the built artifact and the resolved runtime JARs
  on its classpath. Check that each classpath entry is a JAR file.
- Check that `value_type/core.clj` resolves to a `jar:` resource in the
  exact artifact under test. A successful `require` alone is insufficient.
- Test both representation paths with explicit Java executables and options:
  supported Valhalla with `--enable-preview`, and a baseline JVM without
  optional APIs or preview. Construct instances, check fields and factories,
  and check representation-specific equality. Use `Class.isValue` only where
  available; the baseline consumer must not require that API.

Task `009-controlled-deftype-fallback` extended this same isolated consumer
to Temurin `1.8.0_492-b09` (`aa0c8dc`). Equal-field copies compare unequal
on the identity path and equal on the value path. Keep both checks: a
Valhalla-only consumer cannot reveal eager optional dependencies that break
baseline loading. This verifies the recorded JVMs, not all baseline versions.

This tests the packaged dependency contract as well as the library code.

### Select the build-test JVM itself

The value-path consumer in `test-build/build_test.clj` uses the test JVM's
`java.home`, not `VALUE_TYPE_VALHALLA_JAVA`. Run the build suite itself with
`JAVA_CMD=<Valhalla>/bin/java`; setting the cross-JVM variables alone does not
select that consumer. Set `VALUE_TYPE_BASELINE_JAVA` for its baseline consumer.
The fallback suite has a different configuration helper that does use the
Valhalla override. Check each suite's launcher contract when changing CI.

Source: task `011-release-workflow` plan-review handoff (`1c72284`), artifact
tests in `0acef33`, and explicit build-suite selection in workflow `0589c16`.

## Direct function calls do not verify the CLI contract

Calling `build/jar` through `clojure.main` on the build-test classpath
bypasses `:build` alias resolution. It cannot detect a missing tool
dependency or an incorrect `:ns-default`.

Keep direct calls for detailed result checks, but also run the documented
`clojure -T:build jar` and `clojure -T:build clean` commands in a temporary
project. Use an empty `CLJ_CONFIG` and select Java with `JAVA_CMD`.
Verify command results, not only exit status. Test clean without Git
metadata and repeat it with no target directory.

## Artifact correctness does not prove input preservation

Archive comparisons can pass even if the build changes fixture inputs
after packaging. Comparing archive bytes with checkout files does not
detect this defect.

Snapshot the fixture's complete source file paths and bytes, plus
`deps.edn`, before each build or clean command. Compare the snapshot with
the same fixture after the command. This detects additions, deletions and
content changes outside the build's output ownership.

Run destructive checks in temporary projects outside the checkout and
its `target/`. Both build commands delete `target/`, which also holds
benchmark output. Keep fixture cleanup unconditional.
