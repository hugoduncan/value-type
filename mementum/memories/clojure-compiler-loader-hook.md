🔁 A macro can capture `deftype` bytes before JVM definition by setting the
thread-bound `clojure.lang.Compiler/LOADER`. A `DynamicClassLoader` proxy
sends definitions to one delegate. A surrounding `let` keeps the loader
binding; top-level `do` can give forms separate bindings.

Ordinary value-mode definitions install at selection and transform only the target.
Their `(eval (macroexpand form))` path loses that installation; retain the
runtime representation check.

Native restricted-field expansions instead retain a receiving macro inside
`let`. It resolves receiver-local Classes and installs the capture loader
during receiving analysis. Do not also install at selection. This supports
transferred expansions; producer loader state cannot cross serialization.
Retyped owner fields require matching reference changes in companions too.

AOT writes bytes before definition. Write transformed owner and companion
bytes again with `Compiler/writeClassFile`. After `deftype`, use the fully
qualified class symbol; the import has not run at analysis time.

Verified with Clojure 1.12.6 and EA3. Sources: task 016, `2d2db6d`,
`54fd484`, `c8408e6`. See
[mementum compiler boundaries](../knowledge/controlled-fallback-boundaries.md).
