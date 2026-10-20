# Implementation

## Design review: architecture (2026-10-20)

- no architectural review feedback
- Sources: `ramora/META.md` and `doc/architecture.md` do not exist.
  The architecture authority is `ramora/defvaluetype-design.md`. The
  design fits `embodiment.md` "Project artifacts" (deps.edn, `:test`
  with `--enable-preview`, `src/value_type/core.clj`,
  `test/value_type/`), the "Error clarity" quality, and D10 (fail on
  a JVM without value classes).
- Existing files: `.gitignore` already has `/.psi/metrics.edn` and
  `/.psi/project.local.edn`. Append the new entries. The local
  `.psi/project.local.edn` already has `:agent-session` model
  settings. Merge the start command into it. Do not overwrite it.

## Design review: ambiguity (2026-10-20)

- ambiguity review added 1 new design step
- Not filed: how the test detects `--enable-preview` (JVM input
  arguments or a probe). This is a plan choice. Note: `scry.cli` is
  only on the `:test` classpath, and `:test` always adds the flag, so
  this check fails only in a REPL or run without `:test`.

## Design review: inconsistency (2026-10-20)

- no inconsistency review feedback
- Checked against the sources: scry 0.1.61 (Clojars release, tag
  `v0.1.61`), cider-nrepl 0.63.1 (Clojars release) and nREPL 1.8.0
  (on Clojars, not on Maven Central) exist. The scry README confirms
  the CLI exit rule and `.scry-results/`. `~/.m2` does not have
  cider-nrepl 0.63.1 yet. The first run downloads it.

## For the design-steps fix

- Scope is frozen. Fix the path step by a change to the definition or
  the file set in `design.md`. Do not add edits to `spike/valhalla/`
  (out of scope) or to `ramora/` (not in scope).
- Write in ASD-STE100 style (`AGENTS.md`).
- Source for a README placeholder: the JDK download page
  https://jdk.java.net/valhalla/ (from `spike/valhalla/README.md`).
- JDK selection fact (clojure CLI 1.12.6.1673,
  `/opt/homebrew/Cellar/clojure/1.12.6.1673/libexec/bin/clojure`
  line 177): `JAVA_CMD`, then `java` on `PATH`, then
  `$JAVA_HOME/bin/java` only when `PATH` has no `java`. mise puts JDK
  25 on `PATH`. So `JAVA_HOME` alone does not select the Valhalla
  JDK. The README instruction and `.psi/project.local.edn` must set
  `JAVA_CMD` or put `$JAVA_HOME/bin` first on `PATH`. The design
  decision "JDK selection" names only `JAVA_HOME`.

## Design-steps fix (2026-10-20)

- Path step done: design.md "Machine-specific paths" defines the
  term and the covered files. JDK selection changed from `JAVA_HOME`
  to `JAVA_CMD` (fact above). Scope items 5, 6 and criteria 1–3, 6, 7
  follow.
- Observed: in the psi shell, `PATH` has no mise; `java` is the macOS
  stub `/usr/bin/java`, which honours `JAVA_HOME`. In a mise shell,
  JDK 25 is first. So a `JAVA_HOME`-only check can pass here and fail
  for the user. Verify with `JAVA_CMD`.
- `.psi/project.local.edn`: the start command is an argv vector.
  `~` does not expand without a shell. Use the absolute JDK path
  there (the file is not committed).

## Design review: architecture, pass 2 (2026-10-20)

- no architectural review feedback (revised design: `JAVA_CMD`,
  machine-specific paths)
- Not filed: on a JDK without value classes, the environment test
  makes the full suite exit non-zero, but the D11 transform unit tests
  still run there. This is the intent (criterion 2, D10). Later tasks
  can select namespaces with `scry.core/run` to run only unit tests.

## Design review: ambiguity, pass 2 (2026-10-20)

- no ambiguity review feedback

## Design review: inconsistency, pass 2 (2026-10-20)

- no inconsistency review feedback
- psi merges config as defaults < `.psi/project.edn` <
  `.psi/project.local.edn` (`deep-merge`, psi-main
  `shared_config/project.clj`). A local
  `[:agent-session :project-nrepl :start-command]` replaces the shared
  vector and keeps the other local keys.

## After design review, pass 2 (2026-10-20)

- `design-steps.md` has no open items. The three pass 2 reviews made
  no changes to `design.md`. The next step is planning
  (`plan.md`, `steps.md`).
- Scope is frozen. If a later review adds a design step, fix it in
  `design.md` only. Keep ASD-STE100 style, and keep machine-specific
  paths out of the covered files.

## Plan review: ambiguity (2026-10-20)

- ambiguity review added 2 new design steps (`:test` main options,
  `.psi/project.local.edn` start command)
- psi facts (psi-main `components/project-nrepl`): `start-command`
  is validated as a non-empty vector of strings and started with
  `ProcessBuilder` in the worktree (no env, no shell). psi waits for
  a fresh `.nrepl-port` in the worktree. `nrepl.cmdline` writes
  `.nrepl-port` by default, so `:nrepl` needs no port option.
- Not filed: the plan does not choose how the test detects
  `--enable-preview`. All choices meet the design. The smallest is
  `--enable-preview` in
  `(.getInputArguments (java.lang.management.ManagementFactory/getRuntimeMXBean))`.
  Avoid `jdk.internal.misc.PreviewFeatures`: it needs `--add-exports`.

## Plan review: inconsistency (2026-10-20)

- no inconsistency review feedback
- Not filed: Slice 2 says each failure message uses the
  `<valhalla-jdk-home>/bin/java` placeholder. That fix fits only the
  `Class::isValue` check. The preview check fix is "run with
  `-M:test`". The core load check fix is about the source path. The
  design rule covers only the messages that give the JDK fix.

## For the plan-review design-steps fix (2026-10-20)

- The two open items are plan items. Fix them in `plan.md` and
  `steps.md`. `design.md` needs no change. The earlier note
  "fix it in `design.md` only" applies to design items only.
- Keep the smaller choice. Write in ASD-STE100 style.
- `:main-opts`: the Clojure CLI uses the `:main-opts` of the last
  alias that has them, then adds the command line arguments. So
  `:nrepl` main options win in `-M:test:nrepl`.
- Start command facts:
  `~/projects/hugoduncan/psi/psi-main/components/project-nrepl/src/psi/project_nrepl/config.clj`
  (`resolved-start-command`) and `started.clj`
  (`real-process-launcher`). `/usr/bin/env` exists. The absolute
  `java` path `/Users/duncan/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`
  exists. You can write it in `steps.md` (`munera/` is not a covered
  file), but the start command must not use `~`.
- `.psi/project.local.edn` now has only
  `{:version 1, :agent-session {:model-provider … :thinking-level :high}}`.
  Add `[:agent-session :project-nrepl :start-command]` and keep the
  other keys.

## Plan-review design-steps fix (2026-10-20)

- Both plan items done in `plan.md` (key decisions) and `steps.md`
  (Slice 1 steps 2–3, Slice 3 step 3). `design.md` not changed.
- `:nrepl` main options are now exact in `steps.md`:
  `["-m" "nrepl.cmdline" "--middleware" "[cider.nrepl/cider-middleware]"]`.
  `--middleware` takes an EDN vector string.
- The start command uses bare `env` and `clojure`. `ProcessBuilder`
  finds them on the `PATH` of the psi process, as for the shared
  command in `.psi/project.edn`.
- Still open (not filed): Slice 2 asks for the placeholder in each
  failure message; only the `Class::isValue` fix needs it (see
  "Plan review: inconsistency").

## Plan review: ambiguity, pass 2 (2026-10-20)

- ambiguity review added 2 new design steps (environment test
  namespace name, REPL JDK check for psi start)
- scry 0.1.61 discovery fact (`src/scry/clojure_test.clj`): default
  `:dirs ["test"]`, default `:ns-pattern #".*-test$"`.
  `:namespaces` requires the namespaces, then collects the test vars.
- Not filed again: the placeholder in each failure message (see
  "Plan review: inconsistency").

## Plan review: inconsistency, pass 2 (2026-10-20)

- no inconsistency review feedback
- Checked for the pass 2 JDK check item: the Valhalla `java` gives
  `java.vm.version = 27-jep401ea3+1-1` (`java.version` is
  `27-jep401ea3`).

## For the plan-review pass 2 design-steps fix (2026-10-20)

- Fix both items in `plan.md` and `steps.md` only. `design.md` needs
  no change.
- Namespace name: keep the CLI as the design gives it
  (`clojure -M:test -m scry.cli`, no `--ns` or `--dir`). The name must
  end in `-test` so that the default discovery finds it. Put the name
  in the `plan.md` key decision for the environment test, in Slice 2
  step 1 and in Slice 3 step 2.
- JDK check: the smaller choice is to run `scry.core/run` on the
  environment test namespace in the psi REPL. It adds no new check and
  no version string. If you use `java.vm.version`, write the value
  only in `steps.md`. Do not put it in the test code. A version string
  is specific to one JDK build.
- Source for discovery: `~/projects/hugoduncan/scry/scry-master`,
  tag `v0.1.61`, `src/scry/clojure_test.clj` (`default-ns-pattern`,
  `resolve-vars` docstring).

## Plan-review pass 2 design-steps fix (2026-10-20)

- Both pass 2 items done in `plan.md` (key decisions, slice order)
  and `steps.md` (Slice 2 step 1, Slice 3 steps 1, 2, 4).
  `design.md` not changed.
- Name: `value-type.environment-test`,
  `test/value_type/environment_test.clj`.
- REPL JDK check: `scry.core/run` result with `:pass? true` and
  `:summary :var-count 3`. No version string in the steps or tests.
- scry 0.1.61 fact (`src/scry/capture.clj`, `build-result`):
  `:pass?` is only "zero fail and zero error", so it is true when no
  test var runs. `:var-count` is the count of test vars that ran.
  The `:namespace` result format keeps `:summary` and `:pass?`.
- If the test count changes from three, update `:var-count 3` in
  Slice 3 steps 2 and 4 and in the `plan.md` key decision.

## Plan review: ambiguity, pass 3 (2026-10-20)

- no ambiguity review feedback
- Facts for implementation (not filed; the steps are clear enough):
  - JDK 25 `java` for acceptance 2: `mise where java` gives
    `/Users/duncan/.local/share/mise/installs/java/25.0.2`; use its
    `bin/java` (no `Contents/Home`). The psi shell has no mise on
    `PATH`, so give the absolute path.
  - Shell REPL eval (Slice 3 step 2): `clj-nrepl-eval`
    (`~/.local/bin`) or psi `/project-repl attach` (reads
    `.nrepl-port`). Stop the shell nREPL before psi
    `project-repl start` (Slice 3 step 4).
- Not filed again: the placeholder in each failure message, the
  `--enable-preview` detection choice.

## Plan review: inconsistency, pass 3 (2026-10-20)

- no inconsistency review feedback
- Checked: JDK 25.0.2 has no `Class::isValue` and accepts
  `--enable-preview`. So for acceptance 2, expect 1 failing test of 3
  (`Class::isValue`). The preview and core-load tests pass on JDK 25.

## After plan review, pass 3 (2026-10-20)

- `design-steps.md` has no open items. Pass 3 changed no task file
  except this one. The next step is implementation (`steps.md`
  Slice 1).
- If a later review adds a plan item, fix it in `plan.md` and
  `steps.md` only. Keep the smaller choice, ASD-STE100 style, and no
  machine-specific path in the covered files.
- Do not copy from the spike. `spike/valhalla/deps.edn` pins Clojure
  `1.12.1` and has no aliases. `spike/valhalla/src/spike/vt.clj` calls
  `.isValue` directly (line 97). The environment test must get
  `isValue` by reflection (`(.getMethod Class "isValue" ...)`). On
  JDK 25 a direct call fails at compile or call time, depending on
  type hints. Make sure the check fails only as a test failure with
  the intended message. Source for `Class::isValue`:
  `ramora/defvaluetype-design/verification.md` (row 5, line 48).

## Slices 1–2 done (2026-10-20)

- `deps.edn`, `src/value_type/core.clj`, `.gitignore` entries and
  `test/value_type/environment_test.clj` added.
- Preview check: `--enable-preview` in RuntimeMXBean input arguments.
  `isValue` check: `(.getMethod Class "isValue" ...)`, catch
  `NoSuchMethodException`.
- `core-loads-test` has 2 assertions; scry reports 4 assertions,
  3 test vars (`:var-count 3` still holds).
- Verified: Valhalla `JAVA_CMD` → exit 0, 3 tests pass. JDK 25.0.2
  `JAVA_CMD` → exit 1, only `jvm-has-value-classes-test` fails;
  `.scry-results/` has one file. Tool files are ignored.
- Next: Slice 3 (REPL, `.psi/project.local.edn`, psi start).

## Slices 3–4 done (2026-10-20)

- Shell REPL (`JAVA_CMD` = Valhalla, `clojure -M:test:nrepl`):
  `scry.core/run` → `:pass? true`, `:var-count 3`.
- `.psi/project.local.edn`: `:project-nrepl :start-command` merged,
  model keys kept. Not committed (ignored).
- Acceptance 4 check: the exact start-command argv run without
  `JAVA_CMD` in the environment gave the same `:pass? true`,
  `:var-count 3`. The agent cannot call psi `project-repl start`
  from inside its own session; a reviewer can confirm with
  `/project-repl start` in psi.
- README "Development" section added (placeholder only). Covered
  files grep clean for `/Users/`, `~/`, `/home/`.
- `mementum/state.md`: scaffold done; next is implementation tasks.

## Implementation review (2026-10-20)

- added 3 steps to be addressed
- Acceptance 4 step is ticked without a psi `project-repl start` run.

## Review follow-up (2026-10-20)

- addressed 3 review steps
- Acceptance 4: piped `/project-repl start` and `/project-repl eval`
  into a console `psi` in the worktree. Result: `:pass? true`,
  `:var-count 3`; `java.vm.version` `27-jep401ea3+1-1`. So a console
  `psi` can drive psi REPL commands non-interactively.
- `core-loads-test` has 1 assertion now; scry reports 3 assertions.
- README REPL form now has `(require 'scry.core)`.

## Implementation review, pass 2 (2026-10-20)

- no implementation review feedback

## Test review (2026-10-20)

- no test review feedback

## Test review, pass 2 (2026-10-20)

- no test review feedback

## Docs review (2026-10-20)

- no docs review feedback
- Not filed: the README title link has broken Markdown
  (`(text)[url]`) and a `utm_source` query. It is from before this
  task (`0a14d09`), so it is out of scope.

## Code shape review (2026-10-20)

- added 1 step to be addressed

## Code shape follow-up (2026-10-20)

- addressed 1 review step
- Both predicates return a boolean with no coercion:
  `value-class-support?` uses `(do (.getMethod ...) true)` in the
  `try`; `preview-enabled?` uses `List.contains`. `preview-enabled?`
  has a docstring. Valhalla run: 3 tests pass, exit 0.

## Code shape review, pass 2 (2026-10-20)

- no code shape review feedback
