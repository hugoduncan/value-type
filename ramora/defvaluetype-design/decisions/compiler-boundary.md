# Compiler boundary decisions

Scope: the coupling to Clojure compiler output and the expansion forms. These
decisions change [D1, D3, D4](compiler-and-capture.md) and
[D9](validation-and-errors.md). Basis: residual (stressor) analysis of commit
`5073893f`, recorded in residues-skill `examples/value-type/recommendations.md`.

## D19. Compiler boundary (boundary)

- Subject: the assumptions about Clojure compiler output.
- Choice: one namespace owns every assumption about compiler output:
  `Compiler/LOADER` capture, constructor shape, field order, record suffix,
  and all class definition through `DynamicClassLoader` (user classes and
  probe classes). `loader`, both bytecode backends and the probes use it.
  An unsupported compiler shape still throws with "Fix: use Clojure 1.12"
  (D6 shape check). It does not select fallback.
- Rejected:
  - Shape assumptions in `loader`, `transform`, the identity backend and
    `core`. A Clojure change then touches more than one boundary.
  - A compiler check that converts a shape mismatch into fallback. Under
    `silent`, an unsupported compiler would give identity classes with no
    message.
- Rationale: one place changes for a new Clojure version. The probes and the
  loader both depend on `DynamicClassLoader` (name rules, static class cache,
  permission to define); one owner makes this link visible.
- Commitment: firm.

## D20. Receiving expansion for all definitions (interface)

- Subject: the expansion of ordinary value definitions.
- Choice: ordinary value definitions (`defvaluetype`, and `defvaluetype*`
  without enabled non-nil fields and without the closed shape) expand to a
  receiving macro, as definitions with enabled non-nil fields already do. The
  receiving macro installs the capture loader during analysis. The expansion
  has no `installed` literal.
- Changes:
  - D1: the ordinary expansion is a receiving macro, not the `let` with
    `check-value-class <class> <installed>`. deftype* D1 changes the same way.
  - D3: no installation during selection. All installation is in receiving
    analysis.
  - D9: the receiving macro throws `:not-installed` at analysis when no
    `LOADER` is bound. One-step `macroexpand` of the public form stops at the
    receiving macro and does not throw. `:not-value-class` remains for an
    identity result on the value-class path.
- Rejected: two expansion styles. In a replayed expansion, the literal
  `installed=true` gives a misleading `:not-value-class` in a new JVM.
- Rationale: one expansion style. Transferred and replayed expansions install
  where they are analysed.
- Qualities: fewer branches. The expansion shape and its transfer and AOT
  tests change.
- Commitment: firm.
