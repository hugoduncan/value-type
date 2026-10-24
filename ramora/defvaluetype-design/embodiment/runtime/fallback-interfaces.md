## Selected fallback interfaces

`impl.runtime/capability` returns the cached capability map (`:available?`,
and `:reason` plus `:detail` when unavailable). `enforce-fallback!` takes a
diagnostic context and returns nil or throws Error. Initial selection passes
unavailable-capability context; later guards pass fixed-representation context.
All calls share policy and the atomic warning flag. Requiring alone is silent.

The fallback expansion is a `do` containing `impl.runtime/guarded-deftype`.
The barrier preserves this analysis macro through both macroexpand entry
points. The macro checks receiving policy before deftype analysis and emits
another check before runtime deftype evaluation. It preserves all original
options and specs. It is expansion-visible, not an application API. Neither
guard changes representation or performs per-instance checks.

Only the available branch in core resolves loader/install! lazily. There is
no dependency cycle. The detector never loads the loader or transformer.
See the task 009 plan for exact absence classification and implementation.md
for production evidence. Guards do not intercept direct class loads or promise
that no AOT class has loaded before namespace rejection.
