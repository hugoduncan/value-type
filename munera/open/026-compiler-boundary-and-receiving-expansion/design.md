# Compiler boundary and receiving expansion

## Intent

Put all coupling to Clojure compiler output in one boundary, use one
expansion style for all definitions, and protect value-class AOT output on a
JVM where value classes are unavailable. Realize defvaluetype D19, D20 and the
AOT initialization check of D17 and D18.

Priorities, in order: correct, clear failures; one place to change for a new
Clojure version; one expansion style; startup cost of AOT applications.

## Problem and context

- Assumptions about compiler output are in `loader`, `transform`,
  `identity-backend` and `core` (record shape detection). Each probe also
  makes its own `DynamicClassLoader`. A Clojure change touches several
  boundaries. The probes and the loader share a hidden dependency on
  `DynamicClassLoader`.
- Ordinary value definitions expand to `check-value-class <class> <installed>`
  with a literal. A replayed expansion carries `installed=true` into a new JVM
  and gives a misleading `:not-value-class`. Definitions with enabled non-nil
  fields already use a receiving macro.
- Value-class AOT output does not run the probe. On a capable JVM with a
  failed self-check (task 023) or the switch `off` (task 025), it loads as
  value classes.

Source: residual (stressor) analysis, residues-skill
`examples/value-type/recommendations.md`. Design: defvaluetype D19, D20
(`decisions/compiler-boundary.md`), D17 and D18
(`decisions/probe-and-format.md`); changed D1, D3, D4, D9; deftype* D1.

## Concepts

- Compiler boundary: the namespace that owns the model of compiler output and
  class definition through `DynamicClassLoader`.
- Receiving macro: an expansion step that installs the capture loader during
  its own analysis.

## Scope

Include:

- One compiler-boundary namespace: `Compiler/LOADER` capture, constructor
  shape, field order, record suffix, and one define function for user and
  probe classes. `loader`, both bytecode backends, `core` shape detection and
  both probes use it.
- Unsupported shapes keep their current errors and "Fix: use Clojure 1.12".
- Ordinary value definitions of `defvaluetype` and `defvaluetype*` expand to
  a receiving macro. `check-value-class` loses its `installed` argument.
- Value-class expansions check the cached value-class result where the form
  is evaluated, before the class is used. When it is unavailable, throw
  `ex-info` `:value-type/error :value-class-unavailable` with `:reason`.
- A startup measurement of the AOT check on EA3, recorded in
  `implementation.md`. Report it to the user; D17 keeps the check provisional
  until then.

Exclude:

- Fallback for compiler shape mismatches (rejected in D19).
- Support for other Clojure versions.
- Changes to identity output, guards or policy.

## Contract

- Behaviour of direct evaluation, source loading and AOT does not change on
  supported JVMs, except the new AOT check.
- `:not-installed`: thrown by the receiving macro at analysis without a
  `LOADER` binding, for all definitions. One-step `macroexpand` of a public
  form stops at the receiving macro and does not throw.
- A replayed ordinary expansion in a new JVM installs at analysis and defines
  a value class on a capable JVM.
- Value-class AOT output, loaded where the cached result is unavailable with
  `:failed-self-check` or `:disabled`, throws `:value-class-unavailable` with
  that reason.

## Architecture

- Dependency direction: `core`, `loader`, backends and probes depend on the
  compiler boundary; it depends only on Clojure. The boundary loads on Java 8.
- The AOT check reads the owner accessor (`impl.capability/capability`), as
  all readers do (D14).

Rejected: keep two expansion styles; compiler fallback; a separate AOT probe.

## Qualities and risks

- Better: maintainability, transfer correctness, AOT safety.
- Cost: the expansion shape changes; transfer, AOT and D9 tests change.
- Risk: AOT applications run the probe at startup (loads
  `java.lang.classfile`). Measure.
- Risk: closed-record emits `defvaluetype*`; its expansion changes. Check its
  tests.

## Acceptance

1. No namespace other than the compiler boundary constructs
   `DynamicClassLoader` or reads `Compiler/LOADER`. Shape errors and messages
   are unchanged.
2. Tests: one-step `macroexpand` does not throw; full expansion without
   `LOADER` throws `:not-installed`; a replayed ordinary expansion defines a
   value class in a fresh JVM; `defvaluetype*` the same.
3. AOT tests on EA3: value-class AOT output under `value-type.value-classes=off`
   throws `:value-class-unavailable` `:disabled` at namespace initialization;
   a controlled failed self-check gives `:failed-self-check`.
4. Startup measurement recorded with method and limits.
5. All runtime, fallback, cross-JVM, build and tooling suites pass, including
   the `deftype` compatibility corpus. Lint, format, Vale and API checks pass.
6. Docs: D1, D3, D9 and deftype* D1 rewritten for the single expansion style
   (remove the D20 notes), runtime interfaces and data flow, usage guide,
   `CHANGELOG.md`, the embodiment gap table (D17 AOT, D18 AOT, D19, D20),
   `mementum/state.md`.

Create `plan.md`, `steps.md` and `implementation.md` before execution.
