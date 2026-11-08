🔁 Capture `deftype` bytes through the thread-bound `Compiler/LOADER`.
Keep a receiving macro inside an outer `let`: top-level `do` can give
forms separate loader bindings, and producer loader state cannot cross
serialized expansion.

Every value definition installs capture during receiving analysis, not
selection. Both public macroexpand forms retain the receiving macro, so
replayed and transferred expansions install in the receiver. Full expansion
without a loader binding throws `:not-installed`. Identity definitions use
capture only with enabled non-nil fields or the closed shape; ordinary
identity fallback keeps its separate policy guards.

`impl.compiler` owns compiler calls and shape data in `src`. It depends
only on Clojure and loads on Java 8. Bytecode checks stay in the backends;
`impl.loader` supplies rewrite/check callbacks and owns `:jvm-rejected`.
Keep the boundary define call unwrapped: probes classify raw
`UnsupportedClassVersionError`.

AOT writes bytes before definition. The compiler boundary overwrites them
with transformed owner and required companion bytes before definition.
After `deftype`, use the fully qualified class symbol; the import has not
run at analysis time.

Evidence: task 026, `70393735`, `99bef72d`, `18d487f3`. See
[compiler boundaries](../knowledge/controlled-fallback-boundaries.md).
