# Development quality checks and API documentation

## Intent

Give contributors the same formatting, code lint, prose lint and API
documentation workflow used by Scry. Local commands and automated checks
must detect defects and stale generated documentation before merge.
Adapt the workflow to value-type; do not copy Scry-specific paths or APIs.

## Context and reference

The user requested Scry's clj-kondo, cljfmt, Vale and API-doc tasks and
checks. The supplied `~/projects/hugoduncan/scry` is a worktree container.
The reference checkout is `~/projects/hugoduncan/scry/scry-master`, inspected
at commit `a1ab0e61a01d94abd1677d0b59bdd9bfc3897d53`.

Relevant reference files:

- `bb.edn` and `mise.toml`: task names and tool setup.
- `.pre-commit-config.yaml` and `.pre-commit-hooks/`: local checks.
- `.github/workflows/ci.yml`: formatting, lint and API-doc gates.
- `.vale.ini` and `styles/config/vocabularies/Scry/`: prose rules.
- `deps.edn`, `bb/scry/api_docs.clj` and
  `test-quickdoc/scry/api_docs_test.clj`: Quickdoc generation and tests.

Scry's generated dependency imports have local changes. They are not the
reference contract and must not be copied as project-owned configuration.

Value-type has no Babashka task file, mise setup, prose lint setup, API
reference or CI workflow. Its `.clj-kondo/` currently holds a cache only.
Development dependencies use aliases. The library supports ordinary-JVM
fallback as well as preview-enabled Valhalla value classes. Tooling must
not raise the library's minimum JDK or change either representation.

## Scope and contract

### Commands and setup

Keep Scry's task names and command meanings:

- `bb clj-fmt check|fix [PATHS...]` for explicit paths.
- `bb clj-fmt:check` and `bb clj-fmt:fix` for the project scope.
- `bb clj-kondo:copy-config` to prepare dependency configuration.
- `bb clj-kondo:lint`, failing on warnings and errors.
- `bb vale` to sync styles and lint the selected prose.
- `bb api-docs` to regenerate the committed `doc/API.md`.
- `bb api-docs --check` to check freshness without writing the file.

Use Babashka, mise and Quickdoc as in Scry. Keep tool and documentation
dependencies outside runtime dependencies. Document tool installation,
required JVM setup, dependency configuration, commands and hook setup.
No command may depend on the local Scry checkout or a user's global
configuration. Select and record tool versions during planning.

### Check boundaries

Format and lint maintained Clojure code in `src/`, `test/`, `test-build/`,
`test-fallback/`, `bench/`, `build.clj`, and tooling or tooling-test paths
introduced by this task. Exclude generated output, dependency imports,
caches and historical `spike/` code. Account for `defvaluetype` binding
and method syntax without broad suppression of useful lint findings.

Vale covers `README.md`, prose in `AGENTS.md`, and `doc/API.md`. Use Scry's
Vale and proselint style baseline with value-type vocabulary. Preserve
code blocks and lambda-form instructions; do not rewrite those as prose.
Keep `munera/`, `mementum/`, `ramora/` and historical evidence outside this
initial gate. Check generated prose by fixing its source and regenerating.
Local and CI prose checks must use the same scope and failure threshold;
report suggestions and fail on warnings and errors.

Fix existing findings within this scope so the new baseline passes.
Changes to library and test code must preserve behavior. Use narrow,
explained exceptions only where syntax or intentional test cases require
them; do not disable checks merely to obtain a passing baseline.

### API reference

Generate deterministic Markdown with Quickdoc from source-controlled
configuration and docstrings. Document the user-facing
`value-type.core/defvaluetype` macro, its arguments and current contract.
Exclude private vars, `value-type.impl.*`, and the expansion helper
`check-value-class`; its public visibility is not a user API promise.
Do not change var visibility in this task.

Link the README and API reference in both directions. Include source
links for `https://github.com/hugoduncan/value-type` on `master`. State
that the file is generated and give regeneration and check commands.
Keep fallback and value-class requirements consistent with the README.
Avoid duplicating long usage examples in generator-owned prose.

The freshness check must fail for a missing or changed file, give a useful
diagnostic, and leave the file unchanged. Repeated generation must produce
identical bytes. Focused tests must check public API inclusion, internal
API exclusion, and important curated prose, not just freshness. Scry's
records note that freshness alone cannot detect stale generator-owned
examples.

### Automated integration

Provide pre-commit formatting and clj-kondo hooks following Scry's workflow.
Document whether formatting changes files. Do not silently stage unrelated
or previously unstaged edits.

Run formatting, clj-kondo, Vale, API freshness and focused generator tests
in GitHub Actions on pull requests and pushes to `master`. CI checks must
not repair files. They must fail on tool or setup errors, not skip checks.
Use documented setup that works from a fresh checkout, without warmed
caches or pre-existing dependency imports. Do not copy Scry's release,
Kaocha or unrelated test jobs.

## Acceptance criteria

1. The documented setup and all listed commands work from a fresh checkout.
   All checks pass on the committed baseline.
2. Controlled formatting, code-lint and prose violations cause nonzero
   check exits. The formatting fix command repairs a formatting violation.
3. Missing or stale API output fails `bb api-docs --check` without changing
   files. Regeneration restores success and is deterministic.
4. API-content tests enforce the selected public surface and prose
   contract. Generated output has valid repository and README links.
5. Local hooks and CI enforce the stated scope. Controlled failures block
   the relevant hook or CI check. Check commands leave tracked files intact.
6. Tooling remains development-only. Existing library, fallback and build
   suites pass with their documented JVM configurations after baseline fixes.
7. The README describes installation, commands, check boundaries, generated
   files and hook behavior. No absolute local path is required.

## Out of scope

Runtime features, API redesign, benchmark changes, release or publication
automation, a new runtime-test CI matrix, and cleanup of historical records
or spikes. Exported consumer lint support beyond what these repository
checks need can be a separate task.
