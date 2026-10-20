# Design steps

- [x] Define "a path that is specific to one machine" (Invariants,
  acceptance criterion 7) and the set of files that the rule covers.
  - Problem: committed files already have
    `~/.local/jdks/jdk-27.jdk/Contents/Home`: `mementum/state.md`,
    `ramora/defvaluetype-design/embodiment.md`,
    `spike/valhalla/README.md` and this `design.md`. This task changes
    `mementum/state.md` (scope item 7). So criterion 7 is false as
    written, or the update must remove the path.
  - Decide: is a home-relative path (`~/...`) specific to one machine?
    Does the rule cover all committed files, or only the files that
    this task creates (`deps.edn`, `src/`, `test/`, `.gitignore`, the
    README "Development" section)?
  - Decide: what the README gives for `JAVA_HOME` (a placeholder, or
    the verified path as an example).

## Plan review: ambiguity (2026-10-20)

- [x] Make the `:test` alias main options exact in `plan.md` and
  `steps.md` (Slice 1, step 2).
  - Problem: the step says "main option default such that
    `clojure -M:test -m scry.cli` works". It does not say if `:test`
    has `:main-opts`. With `:main-opts ["-m" "scry.cli"]` in `:test`,
    the CLI appends the command line arguments. Then
    `clojure -M:test -m scry.cli` gives `-m scry.cli -m scry.cli`, and
    the Slice 1 check `clojure -M:test -e ...` runs `scry.cli`, not
    the `-e` form.
  - Decide: `:test` has no `:main-opts` (the design gives the CLI as
    `clojure -M:test -m scry.cli`). Only `:nrepl` has `:main-opts`.
- [x] Make the `.psi/project.local.edn` step exact in `plan.md` and
  `steps.md` (Slice 3, step 3).
  - Problem: the step says "a REPL start command that sets
    `JAVA_CMD`". psi `:start-command` is an argv vector of strings
    that psi starts with `ProcessBuilder`. It has no environment key
    and no shell. The step does not say how the command sets
    `JAVA_CMD`. The step also says "Create" and gives the JDK home
    with `~`. The file exists (it has `:agent-session` model
    settings), and `~` does not expand without a shell.
  - Decide: the command form, for example
    `["env" "JAVA_CMD=<absolute-jdk-home>/bin/java" "clojure" "-M:test:nrepl"]`
    with the absolute JDK path; and that the step merges
    `[:agent-session :project-nrepl :start-command]` into the
    existing file and keeps the other keys.

## Plan review: ambiguity, pass 2 (2026-10-20)

- [x] Name the environment test namespace and file in `plan.md` and
  `steps.md` (Slice 2, step 1; Slice 3, step 2).
  - Problem: the step says "an environment test namespace in
    `test/value_type/`" and gives no name. With no arguments,
    `scry.cli` (0.1.61, `scry.clojure-test`) finds only namespaces
    under `test` whose name matches `#".*-test$"`. A name without the
    `-test` suffix gives zero test vars, and the CLI exits non-zero
    on the Valhalla JDK (acceptance 1 fails). Slice 3 step 2 also
    needs the name for `scry.core/run {:namespaces [...]}`.
  - Decide: the name, for example `value-type.environment-test` in
    `test/value_type/environment_test.clj`.
- [x] Give the check that proves the REPL JVM is the Valhalla JDK in
  `steps.md` (Slice 3, steps 1 and 4).
  - Problem: Slice 3 step 1 and acceptance 4 say "starts ... on the
    Valhalla JDK". The steps do not say how to verify the JDK. Step 2
    proves it for the shell REPL (`:pass? true` needs
    `Class::isValue`). Step 4 (psi `project-repl start`) has no check.
  - Decide: the check, for example: in the psi REPL, run
    `scry.core/run` on the environment test namespace and get
    `:pass? true`; or evaluate `(System/getProperty "java.vm.version")`
    and compare with `27-jep401ea3+1-1`.
