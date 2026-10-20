# Steps: 001 Scaffold the project

## Slice 1: project skeleton

- [x] Create `deps.edn` with `:paths ["src"]` and Clojure `1.12.6`
      pinned.
- [x] Add alias `:test`: extra path `test`, dependency
      `org.hugoduncan/scry {:mvn/version "0.1.61"}`, JVM option
      `--enable-preview`. Do not add `:main-opts` to `:test`. The
      command line gives the main: `clojure -M:test -m scry.cli`.
- [x] Add alias `:nrepl`: nREPL `1.8.0` and cider-nrepl `0.63.1`
      pinned, with
      `:main-opts ["-m" "nrepl.cmdline" "--middleware" "[cider.nrepl/cider-middleware]"]`.
      `:nrepl` is the only alias with `:main-opts`, so
      `-M:test:nrepl` composes.
- [x] Create `src/value_type/core.clj` with namespace
      `value-type.core` and a namespace docstring only.
- [x] Add `.gitignore` entries: `.cpcache/`, `.scry-results/`,
      `.nrepl-port`, `/classes/`, `/target/`.
- [x] Verify: with `JAVA_CMD` set to the Valhalla `java`,
      `clojure -M:test -e "(require 'value-type.core)"` loads without
      error (or equivalent sanity check).

## Slice 2: environment test

- [x] Create the environment test namespace
      `value-type.environment-test` in
      `test/value_type/environment_test.clj` with three tests: `Class::isValue` exists (via reflection),
      the JVM runs with `--enable-preview`, and `value-type.core`
      loads. Each failure message names the cause and the fix using
      the placeholder `<valhalla-jdk-home>/bin/java`.
- [x] Verify acceptance 1: with `JAVA_CMD` = Valhalla `java`,
      `clojure -M:test -m scry.cli` exits 0 and at least one test var
      runs.
- [x] Verify acceptance 2: with `JAVA_CMD` = JDK 25 `java`,
      `clojure -M:test -m scry.cli` exits non-zero, the failure
      message names the Valhalla JDK, and `.scry-results/` has the
      failure details.

## Slice 3: REPL

- [x] Verify: with `JAVA_CMD` = Valhalla `java`,
      `clojure -M:test:nrepl` starts an nREPL server and writes
      `.nrepl-port`. The next step proves the JDK.
- [x] Verify acceptance 3: in that REPL, evaluate
      `(do (require 'scry.core) (select-keys (scry.core/run {:namespaces ['value-type.environment-test]}) [:pass? :summary]))`.
      Confirm `:pass? true` and `:var-count 3` in `:summary`. The
      `Class::isValue` test passes only on the Valhalla JDK. Do not
      use `:pass?` alone: it is true when no test var runs.
- [x] In the existing `.psi/project.local.edn` on this machine, add
      `[:agent-session :project-nrepl :start-command]` with the value
      `["env" "JAVA_CMD=/Users/duncan/.local/jdks/jdk-27.jdk/Contents/Home/bin/java" "clojure" "-M:test:nrepl"]`.
      Keep the other keys. Use the absolute path; `~` does not expand
      without a shell. Do not commit this file.
- [x] Verify acceptance 4: psi `project-repl start` starts the REPL.
      In the psi REPL, evaluate the form from the acceptance 3 step.
      Confirm `:pass? true` and `:var-count 3`. This proves that the
      psi REPL runs on the Valhalla JDK.

## Slice 4: documentation and state

- [x] Add a "Development" section to `README.md`: the JDK requirement
      (Valhalla EA with JEP 401, download page
      https://jdk.java.net/valhalla/), how to set
      `export JAVA_CMD=<valhalla-jdk-home>/bin/java`, the test
      command, and the REPL command.
- [x] Check all covered files (`deps.edn`, `src/`, `test/`,
      `.gitignore`, `README.md`) for machine-specific paths; remove
      any found.
- [x] Run the full test command once more and confirm `git status`
      shows no tool-made files.
- [x] Update `mementum/state.md`: scaffold done, next step is the
      implementation tasks starting from `spike/valhalla/src/spike/vt.clj`.
- [x] Record notable decisions or discoveries in
      `implementation.md`.

## Implementation review (2026-10-20)

- [x] Confirm acceptance 4 in psi: run `/project-repl start`, then
      evaluate the form from the acceptance 3 step. Confirm
      `:pass? true` and `:var-count 3`. The step above is ticked, but
      the implementation ran only the start-command argv in a shell
      (see `implementation.md`, "Slices 3–4 done").
- [x] README "Start a REPL": the REPL form calls `scry.core/run`
      without `(require 'scry.core)`. `scry.core` is not loaded at
      REPL start, so the form fails as written. Give the form with
      the `require`, as in the acceptance 3 step.
- [x] `core-loads-test`: remove the `find-ns` assertion. `require`
      returns nil or throws, and `is` reports a throw as an error with
      the message. The second assertion and its copied message add
      nothing. Keep one assertion. `:var-count 3` does not change.

## Code shape review (2026-10-20)

- [x] `environment_test.clj`: make the two predicates consistent.
      `value-class-support?` has a docstring and coerces with
      `some?`; `preview-enabled?` has no docstring and coerces with
      `boolean`. `.getMethod` never returns nil, so `some?` suggests a
      nil case that does not exist. Use one idiom in both (for
      example `(do (.getMethod ...) true)` in the `try`), and give
      `preview-enabled?` a one-line docstring.
