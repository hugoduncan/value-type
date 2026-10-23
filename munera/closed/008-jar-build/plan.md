# JAR build plan

## Approach

Implement the contract in [design.md](design.md). Use the source-only
`build.clj` at
`~/projects/hugoduncan/closed-record/add-jar/build.clj` as the reference.
Do not change `src/` or runtime dependencies.

### Build entry points

- Add a `:build` tool alias with `:ns-default build` and a pinned
  `io.github.clojure/tools.build` dependency. Start with `0.10.10`, the
  version used by the reference. Keep it out of top-level `:deps`.
- Add root `build.clj` with `jar` and `clean`, each accepting the tools
  invocation argument map. Do not load library namespaces or compile
  sources. Do not run Git when the build namespace loads.
- Compute the version inside `jar` with `git rev-list --count HEAD`.
  Trim stdout and accept only `[0-9]+`, as in the reference. Report
  launch failure, nonzero exit and invalid output with clear errors.
  There is no fallback. Return `{:jar-file path :version version}`.
- After version validation, remove `target/`, create a basis with
  `{:project "deps.edn" :user nil :aliases []}`, write Maven metadata,
  copy `src/` and `LICENSE` into `target/classes/`, and create
  `target/value-type-0.1.N.jar`. Do not copy the repository root.
  `clean` only removes `target/`, including when it is already absent.
- Use coordinates `org.hugoduncan/value-type`, description
  "A Clojure library for JEP 401 value objects.", and project URL
  `https://github.com/hugoduncan/value-type`. Adapt the reference SCM
  URL, HTTPS connection and SSH developer connection to this repository.
  Use the reference EPL-2.0 Maven license metadata.
- Add the full, unmodified EPL-2.0 text from the Eclipse license source
  as root `LICENSE`; copy it to the JAR root.

### Automated checks

Keep build checks in `test-build/build_test.clj`. Add a separate
`:build-test` alias with the build namespace path, test directory,
version-matched tools.build, Scry and `--enable-preview`. Keep these
checks out of the default fast library suite. Run them with
`clojure -M:build-test -m scry.cli --dir test-build` on the selected
Valhalla JDK.

Use real temporary directories and Git repositories for integration
checks, with cleanup in `finally`. Child processes must have explicit
working directories, bounded waits and captured exit status/output.
Never run destructive fixture checks in the working checkout. Keep
fixtures outside `target/`, since the build deletes it. Do not use mocks
or add production switches solely for tests.

Separate commit-count result validation from process execution, as in
the reference. Test the validator with result maps for valid whitespace,
nonzero exit and invalid counts (empty, negative and nonnumeric).
Exercise real Git failure in a temporary directory without repository
metadata and Git launch failure in a child JVM with a controlled PATH.

Build a temporary project with known Git history and stale target files.
Inspect the archive and parse its POM and properties. Compare packaged
source paths and bytes with `src/`, and license bytes with `LICENSE`.
Permit standard JAR manifest and Maven metadata entries; reject other
payloads. Check the artifact name, return map and metadata against the
fixture's current commit count. Seed a separate temporary user deps
configuration with an extra dependency and verify it is not in the POM.
Compare POM dependencies with project runtime dependencies, not aliases.

For the consumer check, read the dependency coordinates and versions
from the generated POM into a temporary consumer deps map. Resolve that
map with no user configuration or aliases; do not install or publish the
library artifact. Start Java with only the built JAR and the resolved
runtime JARs on its classpath, with
`--enable-preview`. Do not reuse `run-in-new-jvm` from
`test/value_type/test_support.clj`: it appends the test JVM classpath,
which includes repository sources. Assert that `value_type/core.clj`
resolves from the built JAR. Require `value-type.core`, define a type,
construct instances, and check field values and `Class.isValue`.

## Risks

- Git is required for `jar`, but not `clean`. Shallow history gives a
  partial count; branches can share a version. Document both limits;
  do not add a release policy.
- Both commands delete benchmark output under `target/`. Warn users
  and run automated deletion checks only in temporary projects.
- An inherited test classpath can hide missing JAR contents. The
  consumer must use an explicit, independently resolved classpath.
- User deps or development aliases can contaminate Maven metadata.
  Use an explicit basis and a user-config contamination check.
- Consumer checks require a JEP 401 Valhalla JDK and preview support.
  Missing tools, dependency access or the required JDK are verification
  blockers, not reasons to skip acceptance checks silently.
- Source-only packaging avoids preview class files, but does not remove
  the consumer JDK requirement. Packaging itself must not load sources.

No design decision is blocked. The description wording, dependency pin
and test layout are implementation choices made above, not scope changes.

## Slice order

1. **Build entry points and license** — add the tool alias, version
   validation, clean operation, source-only JAR assembly and EPL-2.0 text.
   Acceptance criteria: 1, 3, 5 and 6.
2. **Build contract checks** — add the separate test alias and automated
   checks for version errors, clean behavior, archive contents, stale
   output and dependency metadata. Acceptance criteria: 1–3, 5–7.
3. **Isolated consumer check** — resolve the generated POM dependencies
   and verify `defvaluetype` in a fresh Valhalla JVM using only the
   artifact and runtime JARs. Acceptance criteria: 4 and 7.
4. **Documentation and final verification** — document build and clean
   commands, limits, license and consumer requirements; run build checks
   and the existing library suite. Acceptance criteria: 7 and 8, with
   final verification of the complete contract.

Commit each completed slice. Record commands, results and decisions in
`implementation.md`, and update the implementation checklist as work
completes.

## Progress

All four slices are complete. Final verification on Valhalla JDK 27 passed:
build suite, 5 tests and 49 assertions; library suite, 64 tests and 332
assertions. Root build commands, returned map and artifact/POM version
agreement are verified. README documents all build requirements and limits.
The design and approach are unchanged. Implementation is ready for review.

Both test-review follow-ups are complete. Automated real CLI checks cover
jar creation and repeated clean without Git metadata in a temporary
project. Source paths, source bytes and deps.edn are unchanged after each
command. Build suite: 6 tests, 59 assertions passed on Valhalla JDK 27.

The documentation-review follow-up is complete. Ramora lists the build
artifacts and explains source-only packaging, with README links and an
index entry. No design or implementation approach changed.
