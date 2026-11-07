🔁 Prove that a JVM did not load a class with a class-load log, not a hook.

To test "X defines no probe class" (task 025, `off` runs no value-class
probe), add `-Xlog:class+load:file=<temp log>` to the child JVM
`:jvm-opts`. After the action, the child check reads the log and returns
`:probe-loaded?`: true when a line names the class (`CapabilityProbe`).
No production hook or counter is necessary.

- Return a boolean, not a count of lines. A count pins JVM log detail
  (test review rounds 2–3).
- Add a positive control: the same check on a configuration that loads
  the class must give `true`. Without it, a wrong class name or an empty
  log also gives `false`.
- Write the log to a temp dir for each child; do not share one file.

Example: `value-class-switch-test/switch-probe-test` (`:export-off` gives
false, `:export-auto` gives true), check
`value-class-switch-checks/probe-loads`.
