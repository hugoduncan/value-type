# Development quality checks: implementation plan

## Approach

Adapt Scry commit `a1ab0e61a01d94abd1677d0b59bdd9bfc3897d53`, not
its working-tree imports. Keep the command names in `design.md`. Add only
development tooling; preserve runtime dependencies, var visibility, fallback
policy, value-class behavior and source-only packaging.

### Tool setup

Use these fixed versions, not Scry's floating `latest` settings:

| Tool | Selected version |
| --- | --- |
| mise | 2026.4.11 |
| Babashka | 1.13.225 |
| Clojure CLI | 1.12.6.1673 |
| clj-kondo | 2026.04.15 |
| cljfmt | 0.16.4 |
| Vale | 3.17.1 |
| pre-commit | 4.6.0 |
| Quickdoc | v0.2.6, `ce8678000afa5f835639dd7267dc191fd90495a2` |
| proselint style package | v0.1.0 |
| Tooling JVM | Temurin 21.0.10+7, no preview |

CLI tool versions were observed locally during planning; Quickdoc and
proselint match Scry. Installation on Linux and cold dependency resolution passed in an Ubuntu
24.04 arm64 container. Hosted Actions execution is not claimed. Pin managed tools in `mise.toml`; document
mise bootstrap separately. Use the same pins in CI. The tooling JVM is not
a runtime minimum. Keep the README's Java 8 and Valhalla test matrix.

Add `bb.edn`, tooling under `bb/value_type/`, and focused tests under
`test-quickdoc/value_type/`. Add `:quickdoc` and `:quickdoc-test` aliases;
the latter supplies Scry 0.1.61. Do not compose value-only `:test` into
tooling commands. Run focused tests with
`clojure -Srepro -M:quickdoc:quickdoc-test -m scry.cli --dir test-quickdoc`.
Use `-Srepro` for tooling dependency resolution and explicit project lint
configuration. Verify with empty user configuration. Ignore downloaded
styles, copied dependency imports and tool caches, not owned configuration.

### Formatting and code lint

Share one maintained path list across project formatting and lint tasks:
`src`, `test`, `test-build`, `test-fallback`, `bench`, `build.clj`, `bb`,
`test-quickdoc`, and root tooling EDN files (`bb.edn`, `deps.edn`). Include
owned Clojure lint hooks if needed. Explicit-path formatting remains
available. Do not traverse `spike`, output, caches or dependency imports.

Resolve dependency configurations for each relevant alias separately and
combine their classpaths. This avoids the `:build` alias's `:deps` replacing
other dependencies and covers both benchmark dependency versions. Prepare
imports before project lint, including from a cold checkout. Fail on setup
errors and on lint warnings/errors. Start with narrow `:lint-as` mappings
for deftype-compatible macros; add an analysis hook only if binding/method
tests show it is needed. Preserve useful findings in macro bodies. Explain
local exceptions for intentional invalid forms. Fix all other scoped findings.

### API generation and prose

Implement `value-type.api-docs` using Quickdoc's in-memory Markdown output.
Read only `src/value_type/core.clj`; use a `:no-doc` override for
`check-value-class`. Quickdoc excludes private vars. Preserve the macro's
existing arglists and docstring contract. Add a short generated notice,
commands and README link, with repository source links on `master`.
Do not duplicate long usage examples.

Separate Markdown generation from file writing and comparison. Use UTF-8
and stable line endings. `--check` compares bytes without writing; missing
or stale output fails with a regeneration command. Invalid arguments fail.
Test the exact documented var set, arguments, fallback/value requirements,
AOT limits, mutable-field restriction, curated prose, links, deterministic
bytes and non-writing failure paths using real files/processes, not mocks.

Configure Vale and proselint v0.1.0 with a ValueType vocabulary. Scope is
exactly `README.md`, prose in `AGENTS.md`, and `doc/API.md`. Exclude lambda
instruction lines narrowly; retain Markdown code-block protection. Use
explicit project configuration with global configuration disabled. Request
Vale JSON with alert-based process failure disabled (`--no-exit`), report
suggestions, and derive failure from warning/error alerts. Propagate tool,
sync and parse errors; verify that `--no-exit` does not hide setup errors.
Local and CI both run `bb vale`.
Fix generated prose in docstrings or generator inputs, then regenerate.

### Hooks and CI

Use local formatting-fix and project-lint pre-commit hooks. Select maintained
Clojure/EDN files, exclude historical/generated paths, and trigger lint for
owned lint configuration changes. Formatting may edit supplied files but
must never run `git add`; changed files block the commit for manual review
and staging. Test partial staging and unrelated edits in disposable repos.

Add one quality workflow for pull requests and pushes to `master`, using
`actions/checkout@v4` and `jdx/mise-action@v2` (mise 2026.4.11).
Use mise alone to install Java and Clojure from the shared pins; duplicate
setup actions could select a different tool through PATH. Disable the mise
action cache for initial installation verification. Install pinned tools,
prepare imports, then run formatting check, lint, Vale, API freshness and
focused generator tests. No fix commands, release jobs or runtime-test
matrix. Check tracked-file integrity after checks, including failure paths.

## Risks

- Dependency/style downloads and tool assets can fail. Fail visibly; verify
  without warm caches. No local Scry or global configuration is a dependency.
- Macro lint support may hide real errors. Verify a valid field/method form
  and an unresolved symbol inside a method before accepting configuration.
- Formatter and prose fixes can alter intentional tests or contract wording.
  Review behavior-sensitive diffs and run all existing suites.
- Vale JSON and exclusions need real negative tests; suggestions alone must
  pass, and warnings/errors must fail without suppressing setup failures.
- Hook formatting can conflict with unstaged hunks. Test pre-commit's restore
  behavior and ensure the index never gains edits from the hook.
- Full regression needs Java 8 and supported Valhalla executables. Record
  missing prerequisites as blockers, not skipped success. Build tests delete
  `target/`; preserve needed output before running them.

## Slice order

1. Pinned setup, task entry points and dependency-config preparation.
   Host and Linux installation verification are complete.
2. Formatting/lint configuration, macro analysis and clean code baseline.
   `defvaluetype` lint-as mapping and real-linter regression tests are done;
   `with-temp-dir` binding analysis and `defpair` type analysis are also verified.
   `defpass` analysis is verified, including type hints and generated arities.
   Custom assertion analysis is verified with namespace- and call-scoped exclusions.
   Formatting and lint baselines pass, including controlled defect probes.
   Intentional partial-protocol cases use form-local exclusions.
3. API generator, content/process tests and committed reference.
   Generation, content/file tests and real CLI process tests are complete.
   Missing/stale output, invalid arguments, diagnostics, non-writing checks
   and repeated generation pass through the Babashka entry point.
4. Vale configuration, shared severity handling and clean prose baseline.
   Complete: shared command, baseline, real severity/scope/setup checks and
   invalid JSON tests pass.
5. Safe pre-commit hooks and read-only GitHub Actions quality checks.
   Hooks and Linux command verification are complete. Run `bash test-tooling/hooks.sh` for real Git,
   pre-commit, formatter and lint integration checks.
   The workflow is added and passes actionlint; its five
   gates pass locally on macOS. Controlled gate and setup failure probes pass
   through `bash test-tooling/ci-failures.sh`, with tracked-file preservation.
   The same commands and failure probes pass on Ubuntu 24.04 arm64,
   with cold tool/dependency installation and unchanged tracked files.
6. Contributor documentation, cold-checkout failure probes and full regression.
   Complete: the README procedure and final acceptance probes pass in a fresh
   clone with empty user configuration and cold project caches (slice 6c).
   The full library, fallback and build runtime matrix passed in slice 6b.
   All seven acceptance criteria have evidence; implementation is ready for review.

Each slice includes verification in `steps.md`. Record commands, outcomes
and justified exceptions in `implementation.md`. Do not close the task
until all seven design acceptance criteria have evidence.
