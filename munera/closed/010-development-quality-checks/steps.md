# Implementation checklist

## Slice 1 — Pinned setup and dependency configuration

- [x] Add `mise.toml` with the plan's managed tool pins; verify installation and version output on the development host.
- [x] Verify pinned tool installation and version output on Linux with the CI checks in slice 5.
- [x] Add `:quickdoc` and `:quickdoc-test` aliases with the selected dependencies and paths; verify runtime dependencies and the build basis remain unchanged.
- [x] Add `bb.edn` task entry points for explicit/project formatting, dependency-config preparation and project lint, using one maintained path list from the plan.
- [x] Implement `bb clj-kondo:copy-config` with separate dependency resolution for `:test`, `:build`, `:build-test`, `:fallback-test`, `:bench`, `:object-size`, `:quickdoc` and `:quickdoc-test`; verify imports with no existing cache.
- [x] Add ignore rules for generated imports, downloaded styles and new caches; verify owned configuration remains tracked.
- [x] Verify dependency resolution with empty user configuration and no Scry checkout dependency; record tool versions and exit status in `implementation.md`.

## Slice 2 — Formatting and code lint

- [x] Add project cljfmt and clj-kondo configuration, including automatic dependency-config preparation before lint and failure on warnings/errors.
- [x] Configure `defvaluetype` analysis as `deftype`; verify valid bindings/methods and constructor calls pass, and an unresolved method-body symbol fails with the real linter.
- [x] Add `with-temp-dir` analysis; verify body bindings, unresolved symbols and binding scope with the real linter.
- [x] Add `defpair` analysis; verify implicit fields, methods, both constructors and retained diagnostics with the real linter.
- [x] Add `defpass` analysis; verify implicit bindings, type substitution, generated arities and retained expression diagnostics with the real linter.
- [x] Add narrow analysis for intentional custom assertion syntax; real-linter tests verify argument checks and calls outside `is`.
- [x] Run project formatting fix over the maintained paths; review the diff for behavior changes and excluded-file edits.
- [x] Verify a controlled formatting defect fails without changing bytes; verify explicit fix and check repair it in a disposable checkout.
- [x] Fix scoped code-lint findings; document each required syntax/test exception beside its narrow configuration.
- [x] Verify all explicit/project formatting commands and project lint pass on the baseline; record exits and reviewed exceptions.
- [x] Inject formatting and code-lint defects in a disposable checkout; verify check failures, formatting repair, and unchanged bytes after check-only commands.

## Slice 3 — Generated API reference

- [x] Add `bb/value_type/api_docs.clj` with in-memory Quickdoc generation, the selected public surface, macro arglists/docstrings, generated notice, commands and repository/README links.
- [x] Add UTF-8 write/check operations and CLI argument handling; expose both `bb api-docs` commands without the value-only test alias.
- [x] Add focused content tests in `test-quickdoc/value_type/api_docs_test.clj` for the exact macro surface, private/helper/implementation exclusion, arguments, curated prose and links.
- [x] Add content assertions for fallback policy, value-class requirements, AOT limits and the mutable-field restriction, without duplicating the entire generated document as a fixture.
- [x] Add real-file/process tests for missing/stale output, useful diagnostics, invalid arguments, non-writing checks and deterministic repeated generation.
- [x] Generate `doc/API.md` and add its README link; verify the reverse link and source-link paths/anchors target this repository on `master`.
- [x] Run the plan's focused Scry command on the tooling JVM without preview; record exit status and structured failures, if any.

## Slice 4 — Prose checks

- [x] Add `.vale.ini` and ValueType vocabulary with the pinned proselint package and narrow lambda/code exclusions; verify direct Vale checks on the three-file scope.
- [x] Enforce the exact three-file scope in the shared `bb vale` entry point.
- [x] Implement `bb vale` style sync and JSON severity handling with explicit project configuration; report suggestions and fail on warnings, errors or setup/parse failures.
- [x] Fix scoped prose findings at their source, preserving lambda instructions, code examples and API meaning; regenerate API output after source changes.
- [x] Verify suggestion-only input passes and warning/error fixtures fail using real Vale runs in a disposable checkout.
- [x] Verify missing styles or invalid configuration fails, code/lambda content remains intact, and ordinary prose in `AGENTS.md` is still checked.
- [x] Run `bb vale`, API freshness and focused generator tests on the revised baseline; record outcomes.

## Slice 5 — Hooks and CI

- [x] Add executable local formatting-fix and project-lint hook scripts plus `.pre-commit-config.yaml` with the plan's scope; omit all automatic staging.
- [x] Test hooks in disposable Git repositories for clean files, formatting changes, lint failures, partial staging, unrelated unstaged edits and excluded paths; compare index and working-tree content.
- [x] Add `.github/workflows/ci.yml` for pull requests and pushes to `master`, with pinned setup, dependency imports and all five quality gates from the plan.
- [x] Validate workflow syntax and run its exact commands from a clean Linux checkout with no imports; verify no check repairs tracked files.
- [x] Run controlled failures through each CI gate, including focused generator-test failure and a tool/setup failure; verify nonzero status and tracked-file preservation. Verified locally with `bash test-tooling/ci-failures.sh`; Linux command execution also passed in an Ubuntu 24.04 arm64 container; no hosted Actions run is claimed.

## Slice 6 — Documentation and final acceptance

- [x] Update README development instructions for pinned installation, tooling versus runtime JVMs, dependency preparation, every task, focused tests, scopes, generated files and hook setup/edit behavior; use no required absolute local paths.
- [x] Follow the README from a fresh checkout with empty user configuration and cold project caches; record commands, installation prerequisites and results.
- [x] Repeat missing/stale API probes and two regenerations in a disposable checkout; compare bytes and verify every check-only task leaves tracked files unchanged.
- [x] Run library tests on supported preview-enabled Valhalla using `clojure -M:test -m scry.cli`; record status and inspect Scry failure EDN if present.
- [x] Run `clojure -M:fallback-test` on Java 8 and Valhalla without preview, plus `clojure -J--enable-preview -M:fallback-test` on Valhalla; record each result.
- [x] Run `clojure -M:build-test -m scry.cli --dir test-build` on preview-enabled Valhalla with both consumer JVM paths supplied; verify artifact isolation remains intact.
- [x] Run all final quality checks and focused tests after documentation edits; record exits and confirm a clean tracked-file diff from checks.
- [x] Map evidence to all seven design acceptance criteria in `implementation.md`; record remaining blockers explicitly and update task progress only for verified actions.

## Test-review follow-up

- [x] Test prose protection with the repository's `.vale.ini`, not only the replacement configuration in `prose-command-test`. The test currently supplies its own `BlockIgnores`, so removal or broadening of the production exclusion can pass the suite. In a disposable project, retain the owned configuration and vocabulary; verify that lambda lines and fenced code pass while a real warning/error in ordinary `AGENTS.md` prose fails, with input bytes unchanged.
- [x] Replace the synthetic failing assertion in `test-tooling/ci-failures.sh` with a controlled defect in the API generator's curated output. The current probe tests Scry's assertion/EDN behavior rather than this task's generator contract. Keep the existing focused tests unchanged, inject a missing required notice or link in the disposable generator, and verify that the documented test command fails without changing tracked files. Do not require a runner-specific failure filename.
- [x] Add a warning-only clj-kondo failure probe through `bb clj-kondo:lint` and the lint hook. Current negative lint fixtures produce errors, so changing the failure threshold from warning to error can pass them. Use valid code with a real warning, confirm there are no error findings, and verify nonzero status plus unchanged file/index content.

## Documentation-review follow-up

- [x] Add the development quality workflow to `ramora/` and make it discoverable from `ramora/INDEX.md`. The current artifact description in `ramora/defvaluetype-design/embodiment/artifacts.md` omits the new tooling aliases, generated API reference, quality commands, hooks and CI. Add a short description of the development-only tooling boundary and link to the README's setup, check scopes and hook behavior rather than copying the full instructions. Include the generated API source and regeneration/check commands, and distinguish quality CI from the separate runtime-test matrix.

## Code-shape review follow-up

- [x] Make the maintained scope fail closed in `bb/value_type/quality.clj`. Both `format!` and `lint!` silently remove missing entries with `filterv fs/exists?`. This was useful during setup, but all listed paths now exist: a missing file or directory can reduce the checked scope without a diagnostic. Use one shared scope validator that reports missing paths before running checks; keep explicit-path formatting behavior separate. Add a disposable-project check that removes a required scope path and verifies a nonzero exit and a useful path diagnostic without file edits.
- [x] Separate type substitution from generated-function construction in `.clj-kondo/hooks/value_type.clj`'s `defpass` hook. The nested `function-form` callback currently owns the tree walk, symbol and metadata replacement, arity selection, binding construction and node conversion. Extract a small private pure substitution function with explicit type/form arguments, leaving the callback responsible for the function form. Keep the hook independent of the preview-only benchmark namespace. Verify the existing real-linter cases for `T`, `T.`, `^T`, both arities and retained diagnostics.
