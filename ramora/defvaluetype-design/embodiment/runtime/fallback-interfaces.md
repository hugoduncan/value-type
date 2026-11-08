## Selected fallback interfaces

`impl.capability/capability` returns the cached capability map (`:available?`,
`:probe-class` (the value probe Class) when available, and `:reason` plus
`:detail` when unavailable). `enforce-fallback!` takes a
diagnostic context and returns nil or throws Error. Initial selection passes
unavailable-capability context; later guards pass fixed-representation context.
All `enforce-fallback!` calls share the policy and the atomic warning flag
in `impl.runtime`. The native-field guard
`impl.field-capability/require-field-capability!` reads only the cached
field result, not the policy. Requiring alone is silent.

For ordinary fallback without enabled non-nil fields or the closed shape,
the expansion is a `do` containing `impl.runtime/guarded-deftype` (or
`guarded-deftype*` for `defvaluetype*`).
The barrier preserves this analysis macro through both macroexpand entry
points. The macro checks receiving policy before deftype analysis and emits
another check before runtime deftype evaluation. It preserves all original
options and specs. It is expansion-visible, not an application API. Neither
guard changes representation or performs per-instance checks.

Fallback with enabled non-nil fields or the closed shape instead emits a
receiving macro inside a `let`. `identity-receiving` enforces fixed fallback
policy during analysis, installs capture and emits another policy check before
the definition. Identity output never calls `check-value-class`.

Receiving analysis lazily resolves `loader/install!` for both representations:
all value definitions, and identity definitions with enabled fields or the
closed shape. The loader selects the baseline-safe identity backend (bundled
ASM) or the value backend. Only the value backend loads the optional JDK
transform. There is no dependency cycle. The detector never loads the loader
or transformer.
See the [capability boundary](../../fallback/capability-boundary.md)
for absence classification. Guards do not intercept direct class loads or promise
that no AOT class has loaded before namespace rejection.
