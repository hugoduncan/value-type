# JAR build implementation steps

## Slice 1 — Build entry points and license

- [x] Add a `:build` alias to `deps.edn` with tools.build `0.10.10` and `:ns-default build`; verify that top-level runtime dependencies are unchanged.
- [x] Add the full EPL-2.0 text from the Eclipse license source as root `LICENSE`; verify that the text is complete and unmodified.
- [x] Add Git version calculation and result validation to root `build.clj`; accept trimmed decimal counts and report launch failure, nonzero exit and invalid output without a fallback.
- [x] Add `build/clean` to remove `target/` without Git access or library loading; verify that namespace loading does not calculate a version.
- [x] Add `build/jar` to clean target output, create a project-only runtime basis, write the specified Maven metadata, copy sources and license, build the named JAR, and return `{:jar-file path :version version}` without AOT compilation.

## Slice 2 — Build contract checks

- [x] Add the separate `:build-test` alias and `test-build/build_test.clj`; verify that Scry discovers the build tests without changing the default library test suite.
- [x] Add temporary-project and child-process test helpers with explicit working directories, bounded waits, captured results and unconditional cleanup; keep fixtures outside the checkout and its `target/`.
- [x] Add commit-count validator tests for trimmed valid output, nonzero exit, empty output, negative counts and nonnumeric output; verify the expected versions or diagnostics.
- [x] Add subprocess checks for `jar` failure in a directory without Git metadata and with Git absent from the child PATH; verify clear errors and no fallback artifact.
- [x] Add clean-command tests in a temporary project without Git metadata; verify that existing target contents are removed and a second clean succeeds.
- [x] Add a successful build test in a temporary Git project with known history and stale files under `target/`, including `target/classes/`; verify the return map, artifact filename, clean target state and absence of stale archive entries.
- [x] Add archive checks that compare every source file and the root license byte-for-byte, allow standard manifest and Maven metadata entries, and reject compiled classes, dependencies and development files.
- [x] Add parsed POM and properties checks for coordinates, version, description, URL, SCM, license and runtime dependencies; run the build with an extra dependency in a temporary user configuration and verify that it and all development dependencies are absent from the metadata.
- [x] Run the complete build contract suite with `clojure -M:build-test -m scry.cli --dir test-build` on the selected Valhalla JDK; require exit 0 and inspect `.scry-results/` EDN for any failures before proceeding.

## Slice 3 — Isolated consumer check

- [x] Add a temporary consumer fixture that resolves the generated POM runtime dependencies without repository source paths, user dependencies or development aliases; verify the resulting classpath contains only the built JAR and resolved runtime JARs.
- [x] Add a bounded consumer JVM check with `--enable-preview` that verifies the core resource comes from the built JAR, requires `value-type.core`, defines a value type, constructs instances, checks their field values and confirms `Class.isValue`.
- [x] Run the build suite including the consumer check on the supported Valhalla JDK; require a successful child exit, successful value-class assertions and a passing Scry result.

## Slice 4 — Documentation and final verification

- [x] Add README build documentation for `clojure -T:build jar`, `clojure -T:build clean`, the returned path/version, artifact location, source-only contents, Git requirement, shallow-history and branch-version limits, and removal of benchmark output under `target/`.
- [x] Update README license and consumer guidance to identify EPL-2.0 and the Valhalla `--enable-preview` requirement; retain the statement that no release is published and distinguish local artifact creation from publication.
- [x] Document the separate build-test command and its prerequisites in README; verify that the command selects `test-build/` and does not replace the library test command.
- [x] Verify both documented build commands from the repository root after preserving any needed benchmark output; compare the new artifact and Maven version with the current `git rev-list --count HEAD` and verify the returned map through a build invocation.
- [x] Run `clojure -M:build-test -m scry.cli --dir test-build` and `clojure -M:test -m scry.cli` on the supported Valhalla JDK; require exit 0 for both and inspect structured failure files if either fails.
- [x] Record final commands, JDK, test results and acceptance-criterion evidence in `implementation.md`; verify that all eight criteria are covered and no library sources, runtime dependencies or publication commands changed.

## Test review follow-up

- [x] Add automated checks for the documented `clojure -T:build jar` and `clojure -T:build clean` commands in temporary projects. `build-process` calls functions through `clojure.main` with the inherited build-test classpath, so the suite can pass with a broken `:build` alias or missing tool dependency. Check the artifact and clean results through the real CLI; keep the checkout target untouched.
- [x] Check that `jar` and `clean` preserve fixture sources and `deps.edn`. Capture file paths and bytes before each command and compare afterwards. Current archive checks compare with checkout files, not the fixture after the build, and do not detect source or runtime-dependency changes made after packaging. This covers the design constraint that the build owns only generated output under `target/`.

## Documentation review follow-up

- [x] Add the JAR build to the Ramora documentation. `ramora/defvaluetype-design/embodiment.md` lists project artifacts but omits `build.clj`, `LICENSE`, the `:build` and `:build-test` aliases, and `test-build/build_test.clj`; no Ramora page describes the new packaging behavior. Add a concise source-only packaging summary and link to the README build commands, version limits, target-deletion warning and consumer JDK requirements. Distinguish library packaging from consumer AOT output (D7), and make the guidance reachable from the Ramora index.
