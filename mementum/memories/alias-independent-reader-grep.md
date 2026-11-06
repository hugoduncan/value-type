🔁 Before you delete or move a var, find its readers with a grep that does
not depend on the alias.

Test files use different aliases for one namespace (`runtime/`, `r/`, the
full name). Many reads are in quoted child-JVM forms. The compiler does not
check a quoted form, so a missed reader fails only at run time in the child,
often in a suite that runs only in the full gate.

Recipe: list the files that load the namespace, then grep them for the var
name:

```
grep -rlE 'impl\.runtime' src test \
  | xargs grep -nE '/capability([^-]|$)'
```

After the move, files that still load the old namespace give hits for the
new owner. "No hits" is not the pass criterion: read the alias of each hit.
Move each test reader in the same commit as the `src/` change it depends
on.

Source: task `021-capability-report` (plan review pass 3: an earlier note
missed `r/capability` reads in `runtime_test`).
