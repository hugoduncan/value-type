# Compiler boundary decisions

Scope: the coupling to Clojure compiler output and the expansion forms.
[D1, D3, D4](compiler-and-capture.md) and [D9](validation-and-errors.md)
include these decisions. Code: `value-type.impl.compiler`.

## D19. Compiler boundary (boundary)

- Subject: the assumptions about Clojure compiler output.
- Choice: one namespace owns every assumption about compiler output:
  `Compiler/LOADER` capture, constructor shape, field order, record suffix,
  all class definition through `DynamicClassLoader` (user classes and probe
  classes), type tag resolution (`Compiler$HostExpr`), field name munging
  (`Compiler/munge`) and class file output (`Compiler/writeClassFile`).
  In `src`, no other namespace has compiler code use: an import of, a
  reference to or a type hint with `clojure.lang.Compiler`, a nested class
  of it or `DynamicClassLoader`; a binding or read of `Compiler/LOADER`; a
  construction or subclass of `DynamicClassLoader`; a `.defineClass` call.
  Text in docstrings, comments and messages can name these classes. Test
  and `dev` code is outside this rule. The boundary users are `core`, `impl.fields`,
  `impl.loader`, `impl.transform`, `impl.identity-backend`, and the probe namespaces `impl.capability`,
  `impl.field-capability` and `impl.probe-builder`.
  The boundary holds the constructor shape, field order and record suffix
  as data (the shape data): the hidden record fields with their tags and
  delegating arguments, the hidden-field counts of the three record
  constructors, the constructor count and the super constructor of
  `deftype*` output, and functions that derive positions from it (the
  index of each hidden field). The boundary loads on Java 8 and has no
  `java.lang.classfile` code. Thus the bytecode shape checks stay in
  `transform` and the identity backend, read the shape data and throw their
  shape errors.
  An unsupported compiler shape still throws with "Fix: use Clojure 1.12"
  (D6 shape check). It does not select fallback.
- Rejected:
  - Shape facts as literals in `loader`, `transform`, the identity backend
    and `core`. A Clojure change then touches more than one namespace.
  - Bytecode shape checks in the boundary. `transform` uses
    `java.lang.classfile`, which a boundary that loads on Java 8 cannot
    use.
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
  receiving macro, as do definitions with enabled non-nil fields. The
  receiving macro installs the capture loader during analysis. The expansion
  has no `installed` literal.
- Constraints:
  - D1: every value expansion is a receiving macro, including deftype* D1.
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
- Qualities: fewer branches. Transfer and AOT tests must exercise the receiving boundary.
- Commitment: firm.
