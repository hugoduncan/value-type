# Development tools and quality checks

[Development guide](../development.md)

## Install development tools

Run commands from the repository root. Install Git, curl, CA certificates,
unzip, xz and Python 3 with venv support. On Ubuntu 24.04, also install
`build-essential`. Allow network access to tool releases, Maven repositories,
GitHub and Vale style packages for the first run.

Install mise 2026.4.11 using the [mise installer](https://mise.jdx.dev/installing-mise.html):

```sh
curl https://mise.run | MISE_VERSION=v2026.4.11 sh
export PATH="$HOME/.local/bin:$PATH"
mise trust
mise install
```

Review `mise.toml` before you trust the checkout. It pins Babashka, Clojure
CLI, clj-kondo, cljfmt, Vale, pre-commit and Temurin Java 21. Use
`mise exec -- COMMAND` to select these tools, or activate mise in your shell
as described in its installation guide. The commands below assume activation;
without it, prefix each command with `mise exec --`.

Java 21 is for development tools, not a runtime minimum. Unset `JAVA_CMD`
when running quality checks so a previous runtime-test selection does not
replace the tooling JVM. Library tests need the separate [Valhalla setup](runtime.md#jdk-requirement). Tool dependencies are development-only aliases, not runtime dependencies.
Quickdoc v0.2.6 is pinned by commit in `deps.edn`; the proselint style package
is pinned to v0.1.0 in `.vale.ini`.

## Quality checks

Prepare dependency lint configuration, then run the checks:

```sh
unset JAVA_CMD
bb clj-kondo:copy-config
bb clj-fmt:check
bb clj-kondo:lint
bb vale
bb api-docs --check
clojure -Srepro -M:quickdoc:quickdoc-test -m scry.cli --dir test-quickdoc
```

Lint also prepares dependency configuration automatically. Each alias basis
is resolved separately. Tooling uses project configuration and reproducible
Clojure dependency resolution; no global lint configuration or local Scry
checkout is needed. Downloads and imports are ignored by Git.

| Command | Purpose |
| --- | --- |
| `bb clj-fmt check [PATHS...]` | Check explicit paths, or the project scope if omitted. |
| `bb clj-fmt fix [PATHS...]` | Format those paths in place. |
| `bb clj-fmt:check` | Check the project scope without edits. |
| `bb clj-fmt:fix` | Format the project scope in place. |
| `bb clj-kondo:copy-config` | Download dependencies and import their lint configuration. |
| `bb clj-kondo:lint` | Fail on code warnings and errors. |
| `bb vale` | Sync styles, report suggestions, and fail on prose warnings and errors. |
| `bb api-docs` | Regenerate the committed `doc/API.md`. |
| `bb api-docs --check` | Fail on missing or stale API output without writing it. |

Formatting and code lint cover `src/`, `test/`, `test-build/`,
`test-fallback/`, `bench/`, `dev/release/`, `test-release/`, `bb/`,
`test-quickdoc/`, `.clj-kondo/hooks/`,
`build.clj`, `bb.edn` and `deps.edn`. They exclude historical `spike/`
code, generated output, caches and dependency imports.

Vale checks `README.md`, prose in `AGENTS.md`, `doc/API.md`, `doc/usage.md`, and the
development guide, its three linked documents and `CHANGELOG.md`.
Code blocks and lambda instruction lines are protected. Task records,
knowledge records and design evidence are outside the prose gate.
Fix generated API prose in source docstrings or `bb/value_type/api_docs.clj`,
then regenerate it; do not edit `doc/API.md` directly. Generation is
deterministic. The focused tests check API content as well as freshness,
real file operations and command failures. Test failures produce EDN details
in `.scry-results/` and a nonzero exit.

GitHub Actions runs the quality checks and focused tooling tests above on
pull requests and pushes to `master`. It also runs the focused release suite:
`clojure -M:release:release-test -m scry.cli --dir test-release`.
It prepares dependencies from the pinned setup and checks that tracked files
remain unchanged. It does not repair files or run the runtime-test matrix.
Setup and tool errors fail the checks.

## Install commit hooks

```sh
pre-commit install
pre-commit run --all-files
```

Keep mise tools available on the Git process PATH, including for editor
commits. Alternatively, run commits with `mise exec -- git commit`.
The local hooks format selected maintained Clojure/EDN files and run
project lint. Changes to owned lint configuration also trigger lint.
The formatter **can edit files**. A formatting change stops the commit:
review the diff, stage the intended changes yourself, then retry. Hooks
never run `git add` or stage unrelated edits. pre-commit temporarily saves
unstaged edits; conflicting formatter changes are rolled back rather than
merged into the index. Vale and API checks run separately, not in these hooks.

Real-process integration probes run in disposable Git checkouts:

```sh
bash test-tooling/hooks.sh
bash test-tooling/ci-failures.sh
```

These check controlled failures and file/index safety with real tools, not
mocks. They need the same installed tools and dependency access.
