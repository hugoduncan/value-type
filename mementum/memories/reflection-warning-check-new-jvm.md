❌ A reflection-warning check can pass when it checks nothing.

Traps:
- A namespace with no `(set! *warn-on-reflection* true)` after its
  `ns` form prints no warning.
- A `require` in a REPL that already loaded the namespace does not
  compile it again. It prints no warning.
- The compiler writes warnings to standard error. The exit code stays
  0 when there are warnings.

Recipe: load the namespace in a new JVM and read standard error.

```
JAVA_CMD=<java> clojure -M:test -e "(require '<ns>)" 2>/tmp/vt-err.txt
```

Pass: exit 0 and no `Reflection warning` line in `/tmp/vt-err.txt`.
Do the check on each JDK that must load the namespace. Valhalla-only
API (`Class::isValue`) reflects on JDK 25. See
`mementum/knowledge/testing-clojure-generated-classes.md`
("Valhalla-only API in tests").

Source: task `004-defvaluetype-macro` (plan review, pass 2; checked on
Clojure 1.12.6).
