# 001 Scaffold the project

## Intent

Make the smallest Clojure project in which the implementation tasks can
write code, run tests and use a REPL on the Valhalla JDK. Use `scry`
as the test runner.

The intent controls all other parts of this design. When a choice is
not clear, choose the smaller project.

## Problem

The repository has documents only. It has no `deps.edn`, no source
path and no test path. The implementation of `defvaluetype` needs:

- a JVM that has JEP 401 value classes, run with `--enable-preview`;
- a test command with a clear pass or fail result;
- a REPL that psi can start with `clojure -M:test:nrepl`
  (`.psi/project.edn`).

The default JDK on this machine is JDK 25 (mise). JDK 25 has no value
classes. When a test runs on the wrong JDK, the failure must say so.
It must not look like a defect in the library.

## Context

- Design: `ramora/defvaluetype-design.md`. The namespaces are
  `value-type.core`, `value-type.impl.loader`,
  `value-type.impl.transform`.
- Verified environment: `ramora/defvaluetype-design/verification.md`.
  JDK `openjdk-27-jep401ea3+1-1`. On this machine, the JDK home is
  `~/.local/jdks/jdk-27.jdk/Contents/Home`. Clojure 1.12.6. The JDK
  download page is https://jdk.java.net/valhalla/.
- Spike: `spike/valhalla/`. It has its own `deps.edn`. It is not part
  of the library.
- scry (`~/projects/hugoduncan/scry/scry-master`): Clojars coordinate
  `org.hugoduncan/scry`, latest release `0.1.61`.
  - CLI: `clojure -M:test -m scry.cli`. Exit 0 only when at least one
    test var runs and all tests pass. Failure details go to
    `.scry-results/`.
  - REPL: `(scry.core/run {:namespaces [...]})` returns a result map.
- The `clojure` script (CLI 1.12.6.1673) selects `java` in this
  order: `$JAVA_CMD`, then `java` on `PATH`, then
  `$JAVA_HOME/bin/java` only when `PATH` has no `java`. mise puts
  JDK 25 on `PATH`. Thus `JAVA_HOME` alone does not select the
  Valhalla JDK.
- psi starts the project REPL with `clojure -M:test:nrepl`. The file
  `.psi/project.local.edn` is not in git. It can hold settings for
  this machine only.

## Scope

In scope:

1. `deps.edn`:
   - `:paths ["src"]`.
   - Clojure `1.12.6`, pinned.
   - Alias `:test`: extra path `test`, extra dependency
     `org.hugoduncan/scry` pinned to `0.1.61`, JVM option
     `--enable-preview`.
   - Alias `:nrepl`: nREPL `1.8.0` and cider-nrepl `0.63.1`, pinned,
     with main options that start nREPL with the cider middleware. It
     must compose with `:test` as `-M:test:nrepl`.
2. `src/value_type/core.clj`: the namespace `value-type.core` with a
   namespace docstring only. No macro. It makes the source path real.
3. An environment test namespace in `test/value_type/`. It has these
   checks. Each failure message tells the cause and the fix.
   - The JVM has value class support (`Class::isValue` exists).
   - The JVM runs with `--enable-preview`.
   - `value-type.core` loads.
4. `.gitignore` entries for the files that tools make: `.cpcache/`,
   `.scry-results/`, `.nrepl-port`, `/classes/`, `/target/`.
5. `README.md`: a "Development" section. It tells the JDK
   requirement, how to set `JAVA_CMD`, the test command and the REPL
   command.
6. `.psi/project.local.edn` on this machine: set the project REPL
   start command so that it sets `JAVA_CMD` to the `java` of the
   Valhalla JDK. This file is not committed.
7. `mementum/state.md`: update the status and the next step.

## Out of scope

- The `defvaluetype` macro, the loader and the transform. They are
  later tasks.
- Lint and format tools (clj-kondo, cljfmt, `bb` tasks, mise tools).
  This is an adjacent task.
- A build, a jar and deployment to Clojars.
- CI. The Valhalla JDK is an early-access download.
- Changes to `spike/valhalla/`. Removal of the spike is a later
  decision.
- An automatic JDK download or install.

## Decisions

### JDK selection

- Choice: the user environment selects the JDK with `JAVA_CMD`, set
  to the `java` of the Valhalla JDK. The `clojure` script uses
  `JAVA_CMD` first, so `PATH` and mise have no effect. The README
  tells how. The environment test detects the wrong JDK.
- Rejected:
  - `JAVA_HOME` alone. The `clojure` script uses `java` on `PATH`
    before `JAVA_HOME` (see Context).
  - `$JAVA_HOME/bin` first on `PATH`. The mise shell hook can change
    `PATH` again.
  - `mise.toml` with `JAVA_HOME`. The path is different on each
    machine. psi starts the REPL without the mise shell hook, so it
    does not get the value.
  - A wrapper script. More files, and psi does not use it.
- For psi on this machine: `.psi/project.local.edn` sets the start
  command with `JAVA_CMD` for the Valhalla JDK.

### Machine-specific paths

- Definition: a machine-specific path is an absolute path or a
  home-relative path (`~/...`) to a location outside the repository.
  A path relative to the repository root is not machine-specific. A
  URL is not machine-specific.
- Covered files: `deps.edn`, `src/`, `test/`, `.gitignore` and
  `README.md`. These files configure, test or instruct for all
  machines.
- Not covered: `mementum/`, `ramora/`, `munera/` and `spike/`. These
  files record the verified environment on one machine. The update to
  `mementum/state.md` can keep the JDK path.
- The README gives a placeholder, not the verified path:
  `export JAVA_CMD=<valhalla-jdk-home>/bin/java`. The README tells
  that `<valhalla-jdk-home>` is the home directory of the Valhalla
  JDK, and gives the download page. The fix in the environment test
  failure messages uses the same placeholder.

### Versions

Pin all versions. Do not use `RELEASE`. A pinned version gives the
same result on each run. The spike verified Clojure 1.12.6.

### The test is an environment test

The scaffold has no library behaviour to test. A trivial test (for
example `(is true)`) proves only that scry runs. The environment test
also proves that the JDK and the JVM options are correct. This is the
most frequent setup error for this project. The scry CLI needs at
least one test var to exit 0, so the test is also necessary.

### Namespace layout

Follow the design: `src/value_type/` and `test/value_type/`. Create
only `value-type.core`. The implementation tasks create the `impl`
namespaces when they need them.

## Invariants

- The `:test` alias always has `--enable-preview`. The REPL alias gets
  it through `-M:test:nrepl`.
- The covered files have no machine-specific path (see
  "Machine-specific paths").

## Acceptance criteria

1. With `JAVA_CMD` set to the `java` of the Valhalla JDK,
   `clojure -M:test -m scry.cli` exits 0. At least one test var runs.
2. With `JAVA_CMD` set to the `java` of JDK 25,
   `clojure -M:test -m scry.cli` exits non-zero. The environment test fails with a message that names the
   Valhalla JDK. `.scry-results/` has the failure details.
3. With `JAVA_CMD` set to the `java` of the Valhalla JDK,
   `clojure -M:test:nrepl` starts an nREPL server on the Valhalla JDK.
   In that REPL, `scry.core/run` on the environment test namespace
   returns `:pass? true`.
4. psi `project-repl start` starts the REPL on the Valhalla JDK, with
   the start command from `.psi/project.local.edn`.
5. `git status` shows no files that tools made.
6. The README "Development" section has the JDK requirement, the
   `JAVA_CMD` instruction, the test command and the REPL command.
7. No covered file has a machine-specific path (see
   "Machine-specific paths").
8. `mementum/state.md` shows the scaffold as done and gives the next
   step.
