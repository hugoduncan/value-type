# Local acceptance

Verified on macOS/aarch64 at `105085fc04b9116154b619396a152587c7605f4e`
(version `0.1.98`). Documentation-only acceptance commits change the Git-count
version; this is not a prepared release.

## Commands and JVMs

Commands below ran through `mise exec --`. Tooling used Temurin
`21.0.10+7-LTS`. Runtime selections were explicit:

- `VALUE_TYPE_BASELINE_JAVA=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`
  (Temurin `1.8.0_492-b09`).
- `VALUE_TYPE_VALHALLA_JAVA=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`
  (Valhalla `27-jep401ea3+1-1`).
- `JAVA_CMD` selected the indicated runtime JVM for each command. Both
  cross-JVM variables were exported for runtime and build tests.

| JVM | Command | Tests/assertions |
| --- | --- | --- |
| Valhalla | `clojure -M:test -m scry.cli` | 64/332 |
| Java 8 | `clojure -M:fallback-test` | 15/166 |
| Valhalla | `clojure -M:fallback-test` | 15/166 |
| Valhalla | `clojure -J--enable-preview -M:fallback-test` | 15/141 |
| Valhalla | `clojure -M:build-test -m scry.cli --dir test-build` | 9/96 |
| Tooling | `clojure -M:release:release-test -m scry.cli --dir test-release` | 11/112 |
| Tooling | `clojure -Srepro -M:quickdoc:quickdoc-test -m scry.cli --dir test-quickdoc` | 11/172 |

All completed suites exited 0, with no failures or errors. Inspection of
`.scry-results` immediately after each suite found no files. An initial combined
fallback command exceeded the 200-second tool timeout; all three configurations
were rerun successfully with a 1,200-second allowance. No test change was needed.

Formatting, lint, Vale and API freshness commands from plan.md passed. Lint
reported zero warnings/errors. Actionlint 1.7.7 passed on both workflows.
`git diff --exit-code HEAD --` confirmed checks did not change tracked files.

## Local preview and build

With both Clojars variables removed from the environment, these commands passed:

```sh
clojure -M:release -m value-type.release notes /tmp/value-type-011-acceptance/notes.md /tmp/value-type-011-acceptance/outputs --preview
clojure -T:build jar
```

Export reported the SHA above, version `0.1.98`, tag `v0.1.98`, source
`Unreleased` and `preparation-required=true`. Notes remained nonempty after
build. The expected JAR and POM existed and were nonempty. Existing research
output was preserved before build at `/tmp/value-type-011-target.Jsk1Ek/target`.
These temporary paths are local convenience files, not durable evidence.

## Acceptance mapping

1. Slices 1–4 retained the pinned reference contracts and review fixes.
   Runtime source and dependencies are unchanged. The build suite verifies
   source-only packaging, metadata and both isolated POM-derived consumers.
2. Pure notes, real Git/process and credential tests passed. Coverage includes
   rejection before output writes and inspectable partial commit failure.
   Valid credentials are tested without upload; no service mocks were added.
3. The complete local command matrix above passed. Linux archive checks and
   the real value-capability probe are recorded in [linux-jvms.md](linux-jvms.md).
4. Workflow inspection confirms one fixed full-history SHA checkout, master
   guard, typed-false publication gates, step-scoped secrets and checks before
   notes selection. Notes stay outside target. Publication order is credential
   validation, tag/push, deploy, then Release creation with returned paths.
   Default success gates stop later writes after failure; reporting requires
   manual inspection. No outer retry or rollback exists.
5. Operator, runtime and Ramora guides agree on preparation, separate push,
   preview, publication, ownership and manual recovery. Slice 6a and this pass
   verified prose and API checks. Local Markdown file targets and whitespace
   checks passed.
6. No project push, dispatch, secret configuration, deployment, publication or
   benchmark ran. Tests used temporary local Git remotes only. Native hosted
   Actions execution, permissions and live publication remain unverified.

No design deviation or remaining implementation item was found. This evidence
does not claim remote file verification or atomic publication.
