❌ To run on the Valhalla JDK, set `JAVA_CMD`. Do not use `JAVA_HOME`
for this.

The `clojure` script (CLI 1.12.6) selects `java` in this order:
`$JAVA_CMD`, then `java` on `PATH`, then `$JAVA_HOME/bin/java` only
when `PATH` has no `java`. In a mise shell, JDK 25 is first on `PATH`.

Trap for agents: the psi shell has no mise on `PATH`. There, `java` is
the macOS stub `/usr/bin/java`, and the stub uses `JAVA_HOME`. Thus a
check with `JAVA_HOME` only can pass for the agent and fail for the
user. Verify with `JAVA_CMD`.

psi `[:agent-session :project-nrepl :start-command]` is an argv vector.
psi starts it with `ProcessBuilder`: no shell and no env key. Use
`["env" "JAVA_CMD=<absolute-path>/bin/java" "clojure" "-M:test:nrepl"]`
in `.psi/project.local.edn` (not committed). `~` does not expand.

Check the JDK of a REPL with the environment test
(`value-type.environment-test`), or with `java.vm.version`.

Source: task `001-scaffold-project` (design fix `900beea`).
