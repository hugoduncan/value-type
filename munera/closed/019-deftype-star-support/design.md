# Implement deftype* support

## Purpose and priorities

Let libraries that emit `deftype*` obtain value-class output without using
internal loader APIs. Preserve the compiler’s record construction contract
and the application’s authority over identity fallback. The first intended
caller is closed-record; this task delivers the value-type API it needs.

Implement the [deftype* design](../../../ramora/deftype-star-design.md) and
[decisions D1–D7](../../../ramora/deftype-star-design/decisions.md), as the
current files state them. The architecture guide,
`~/projects/hugoduncan/architecture/ramora/INDEX.md`, provides the model:

```text
Purpose + Context → Architecture → Embodiment → Observed qualities
```

Priorities, in order: correct construction and failure behavior; existing
fallback authority; no unused hash storage in value output; a small public
interface. This task does not promise faster execution or a measured object size.

## Problem and context

`defvaluetype` uses `deftype`, which rejects the hidden record field names.
Direct `deftype*` callers therefore have no public value-type entry point.
The current value transform accepts one constructor, but the compiler’s
record shape has a main constructor and two delegating constructors. The
reader and compiler also depend on the generated static `create` method.

Value objects cannot use mutable hash caches. Removing the hash fields must
not remove the constructor parameters that the compiler-generated callers
supply. Preserve constructor descriptors, argument slots, metadata and
ordinary fields, including wide primitive arguments.

The supported verification environments are Clojure 1.12.6, Java 8 and
Valhalla `27-jep401ea3+1-1` (EA3), with preview off and on. Do not raise the
fallback JVM minimum. Clojure 1.11 and other JVM releases are not new support
targets. Optional JDK APIs must remain outside baseline namespace loading.

## Scope and public contract

Provide `value-type.core/defvaluetype*` with the arguments of `deftype*`.
It evaluates to the defined Class. Callers use it in the same top-level
form position as `deftype*`, not inside a method body.

Support:

- Plain forms with one constructor.
- Record forms with the trailing fields `__meta`, `__extmap`, `^int __hash`
  and `^int __hasheq`, in that order and without mutable metadata (see
  Record shape).
- Value output and identity fallback under `value-type.fallback`.
- Source compilation, receiving compilation of the fallback expansion and
  AOT under the existing
  [loader and fallback contract](../../../ramora/defvaluetype-design/fallback.md).

Receiving compilation is the compilation of an expansion from `macroexpand`
or `macroexpand-1` apart from the form that produced it: by `eval`, by
loading its printed source, or by analysis inside a function that is then
called. The guarded fallback expansion enforces the receiver's policy at
analysis and evaluation, as for `defvaluetype`. A value expansion does not
support receiving compilation, as for ordinary `defvaluetype` definitions
(defvaluetype D9). It fails at run time with `:not-installed` when it was
expanded with no compiler `LOADER` binding, and with `:not-value-class` when
it is evaluated apart from its expansion, for example
`(eval (macroexpand form))`. It never gives an identity class and never
selects fallback.

Record callers must not read or write the hash fields and must pass zero
for both hash arguments in explicit constructor calls. Value output removes
both fields and ignores those arguments. Identity output keeps both fields;
they hold 0 when callers meet these obligations. value-type does not
validate constructor arguments.

Reject mutable fields, unsupported record shapes and field metadata with
`:non-nil` or `:nil-restricted` as the rules below state. Captured accesses
to removed fields and unsupported constructor sets must fail with the errors
of the [design](../../../ramora/deftype-star-design.md). Unexpected compiler,
loader and transform failures must remain visible, not trigger fallback.

Exclude closed-record integration, changes to `defrecord`, non-nil support
for this API, mutable caches, value-equality emulation, arbitrary constructor
rewriting, new representation policy, serialization guarantees and performance
benchmarks. Existing `defvaluetype`, non-nil fields and array contracts remain
unchanged. Task 018’s unresolved review items are separate work.

### Record shape

The hidden names are `__meta`, `__extmap`, `__hash` and `__hasheq`. A field
has a hidden name when its symbol name is one of them. The namespace is
ignored, as in the compiler. For a form with n fields:

- Plain shape: no field has a hidden name.
- Record shape: n ≥ 4, and every hidden-name field is in place, and the last
  four fields have hidden names. A hidden-name field is in place when it is
  the unqualified symbol at index n − 4 + k, where k is its index in
  (`__meta` `__extmap` `__hash` `__hasheq`), and it has its required tag.
  `__meta` and `__extmap` have no tag (`:tag` absent or nil). The `:tag` of
  `__hash` and `__hasheq` is the unqualified symbol `int`.
- All other forms give `:invalid-field` with `:reason :record-shape`. Examples:
  `[__meta x]`, the trailing subset `[x __hash __hasheq]`, a tag on
  `__meta`, and `^long __hash`.

`^int` and `^{:tag int}` give the symbol `int`. `{:tag 'int}` gives it when a
macro evaluates that map to build the form. `^{:tag 'int}` written in the
source of the form gives the list `(quote int)`, which is rejected. Other
tags, for example other primitive tags, `Integer`, the string `"int"` or no
tag, are rejected.

`:field` is the first field, in field order, that has a hidden name and is
not in place. When all hidden-name fields are in place (a trailing subset),
`:field` is the first field that has a hidden name. Elements that are not
symbols have no hidden name. A `fields` value that is not a vector gets no
shape check. `deftype*` reports these two faults.

### Field metadata

These rules apply to each field that is a symbol in a `fields` vector,
including the hidden fields, as in `defvaluetype`. As for the record shape,
elements that are not symbols, and a `fields` value that is not a vector,
get no metadata check, whatever their metadata. `deftype*` reports these
faults.

- Metadata that contains the key `:nil-restricted`, with any value, gives
  `:invalid-field` with `:reason :removed-nil-restricted` and the
  `defvaluetype` data (`:field`, `:value`).
- Else metadata that contains the key `:non-nil`, with any value, including
  `false` and `nil`, gives `:invalid-field` with
  `:reason :non-nil-unsupported` and `:field`.

The first such field, in field order, is reported.

### Validation order

The three declaration checks below run at macroexpansion, before
representation policy. Thus an invalid form never selects fallback. When one form has several faults, the
first check that fails gives the error:

1. `:mutable-field`, for all mutable fields, including the hash fields
   (symbols in a `fields` vector only, as in `defvaluetype`);
2. `:invalid-field` `:record-shape`;
3. `:invalid-field` for field metadata.

### Removed fields

Value output removes `__hash` and `__hasheq`. The capture loader rejects each
captured owner or companion class that accesses a removed field, with
`:invalid-field` and `:reason :removed-field-access`. An access is a
`getfield` or `putfield` instruction that names the target class and
`__hash` or `__hasheq`. The main-constructor writes that the transform
removes are not accesses. Reflective access is not checked. The loader
checks each class when it defines it. When one class accesses both fields,
`:field` is the symbol `__hash`.

Identity fallback installs no capture loader and runs no transform. The
fields remain, and the loader does not detect access. The caller obligation
continues to apply.

## Architecture and responsibility

Follow D1–D6 rather than introduce another definition framework:

- The public boundary owns form validation and the Class-valued interface.
- Application policy owns representation. Callers cannot override it.
- The capture boundary owns the compiler loader state and consistent runtime
  and persisted output.
- The value transform owns exact constructor-shape validation, main-constructor
  rewriting and hash-field removal. Compiler-generated delegating constructors,
  `create` and `getBasis` retain their contracts.
- Captured owner and companion code must not access removed fields.
- Identity fallback remains guarded, untransformed `deftype*` output. The
  identity field backend does not acquire record support in this task.

The selected design preserves compiler-generated construction instead of
replacing it. A `defvaluetype` option cannot bypass `deftype` validation;
direct internal loader calls bypass policy; a generic definition wrapper
widens the interface without a required use. Accept tighter coupling to
compiler bytecode in exchange for a narrow, explicitly checked shape.

## Feasibility gate and evidence

D3 is provisional. First retain a runnable EA3 preview experiment that
transforms a record-shaped form and exercises every constructor, `create`,
both record literal forms and a compiled record constant. This must prove
that value-class delegating constructors work with verification enabled.

If this gate fails, record the evidence and stop for a design decision.
Do not silently replace constructors, drop record behavior or claim completion.
Serialization remains unpromised even if the record interface is present.

Record-shaped test forms implement the interfaces that record literals,
record constants and metadata need: `clojure.lang.IRecord`, `java.util.Map`,
`clojure.lang.ILookup`, `clojure.lang.IObj` and `clojure.lang.IHashEq`. They
define these methods:

- `valAt`, both arities, and `entrySet`, over the declared fields and the
  `__extmap` entries. The compiler emits a record constant through `create`
  with a map that it copies from `entrySet`.
- `meta`, and `withMeta` through the meta-and-extmap constructor.
- `hasheq` and `hashCode`. They compute the hash from the entries on each
  call and do not read `__hash` or `__hasheq`.

Other interface methods are not defined and are outside the evidence. The
forms do not implement `clojure.lang.IType`, as `defrecord` output does not.
The compiler checks `IType` before `IRecord`, and emits an `IType` constant
through `getBasis` and the first reflected constructor, not through `create`. Plain
test forms implement `clojure.lang.IType`, as `deftype` output does.

## Acceptance

1. Plain and record-shaped public forms return the defined Class. Valid
   value output is a value class; permitted fallback is an identity class.
   Field metadata and supported methods retain their behavior. Supported
   methods are the methods that a form defines. They behave as in the same
   `deftype*` output, apart from the documented value-class semantics.
2. All three record constructors, `create`, `getBasis`, map and positional
   record literals, compiled record constants and metadata work in both
   representations. Cover zero declared fields, reference fields and mixed
   primitive fields, including long/double argument slots.
3. Inspection of emitted bytes proves that value output has no hash fields,
   keeps constructor descriptors and retains valid delegation. Identity
   output retains immutable hash fields. Construction preserves all kept
   fields. In value output, owner and companion access to removed fields is
   rejected.
4. Invalid declarations and unsupported bytecode shapes produce the design’s
   error keys and data. Negative cases establish rejection, not merely the
   absence of a successful definition.
5. Silent, warn and error policies retain existing behavior and warning
   scope. The transferred fallback expansion, from both `macroexpand` and
   `macroexpand-1`, enforces receiving policy in the three receiving modes
   of Scope. One transferred value expansion fails with `:not-installed`,
   and one fails with `:not-value-class`. AOT fixes the
   representation: separate source-free consumers retain identity policy,
   and incompatible receivers reject value artifacts without conversion.
6. Real JVM evidence covers both shapes on Java 8 and EA3 preview off/on,
   including fresh-process policy and AOT cases. Relevant existing regression,
   packaging, tooling and quality checks pass. Record commands, structured
   results and limits in `implementation.md`; do not infer broader support.
7. Usage and generated API documentation state syntax, caller obligations,
   fallback, exclusions and evidence limits. They use the identity
   hash-field statement of this design. Ramora embodiment describes
   delivered behavior; task progress and verification records remain in Munera.

Create `plan.md`, `steps.md` and `implementation.md` before execution. Planning
must map these criteria to bounded evidence and preserve the feasibility gate.
