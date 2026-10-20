# Plan: 001 Scaffold the project

## Approach

Build the smallest project that satisfies the design, in four slices.
Each slice ends in a verifiable state.

Key decisions (from design.md and the plan reviews):

- JDK selection by `JAVA_CMD` only. The README and the environment
  test failure messages use the placeholder
  `<valhalla-jdk-home>/bin/java`. No covered file gets a
  machine-specific path.
- Pin all versions: Clojure `1.12.6`, scry `0.1.61`, nREPL `1.8.0`,
  cider-nrepl `0.63.1`.
- `--enable-preview` lives only in the `:test` alias. The REPL
  composes it with `-M:test:nrepl`.
- Only `:nrepl` has `:main-opts`. `:test` has no `:main-opts`. The
  Clojure CLI appends the command line arguments to the alias main
  options. Thus `clojure -M:test -m scry.cli` and
  `clojure -M:test -e ...` run as written.
- psi start command on this machine: the argv vector
  `["env" "JAVA_CMD=<absolute-jdk-home>/bin/java" "clojure" "-M:test:nrepl"]`
  at `[:agent-session :project-nrepl :start-command]` in
  `.psi/project.local.edn`. psi starts it with `ProcessBuilder`, with
  no shell and no environment key, so `env` sets `JAVA_CMD` and the
  path is absolute (no `~`). Merge the key into the existing file and
  keep the other keys.
- The single test namespace is an environment test:
  `value-type.environment-test` in
  `test/value_type/environment_test.clj`. It checks that
  `Class::isValue` exists, preview is enabled and `value-type.core`
  loads. Each failure message names the cause and the fix. The name
  ends in `-test`: with no arguments, `scry.cli` finds only the
  namespaces under `test` that match `#".*-test$"`.
- REPL JDK check: in the REPL, `scry.core/run` on
  `value-type.environment-test` returns `:pass? true` and
  `:var-count 3`. The `Class::isValue` test passes only on the
  Valhalla JDK. `:pass? true` alone is not sufficient, because scry
  gives `:pass? true` when no test var runs. The same check applies
  to the shell REPL and to the psi REPL.
- Verify acceptance on both JDKs: Valhalla (pass, exit 0) and JDK 25
  (fail, exit non-zero, message names the Valhalla JDK).

## Risks

- scry `0.1.61` behaviour on exit codes: the design states exit 0
  only when at least one test var runs and all pass. If the installed
  release differs, verify against `.scry-results/` and adjust.
- `Class::isValue` detection: must use reflection so the test
  namespace loads on JDK 25 and fails with the intended message, not
  a compile or load error.
- Wrong-JDK path: loading `value-type.core` must not itself require
  preview features, or acceptance criterion 2 can fail with the wrong
  error. `core.clj` is a docstring-only namespace, so this is low
  risk.
- psi REPL start: `.psi/project.local.edn` is machine-local and not
  committed. psi merges it over `.psi/project.edn`. Verify with
  `project-repl start` on this machine.

## Slice order

1. Project skeleton: `deps.edn`, `src/value_type/core.clj`,
   `.gitignore`.
2. Environment test namespace `value-type.environment-test`, then
   verify the scry CLI on both JDKs.
3. REPL: `:nrepl` alias verification, `.psi/project.local.edn`,
   in-REPL `scry.core/run` check in the shell REPL and in the psi
   REPL.
4. Documentation and state: README "Development" section,
   `mementum/state.md` update, final acceptance sweep.
