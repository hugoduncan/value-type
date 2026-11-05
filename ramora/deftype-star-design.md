# deftype* design

This design follows the architecture model in
`~/projects/hugoduncan/architecture/ramora/INDEX.md`:

```text
Purpose + Context → Architecture → Embodiment → Observed qualities
```

It extends the [defvaluetype design](defvaluetype-design.md). Read that
design first for the capture loader, transform, fallback policy and AOT rules.

## Purpose and priorities

Let a library that emits `deftype*` directly get value-class output. The first
user is closed-record: its `defclosedrecord` emits a record-shaped `deftype*`
and will select value output with a `:value-type true` option.

Priorities: keep the Clojure record contract (record literals, code constants,
the static `create` method); obey the same application fallback policy as
`defvaluetype`; add no per-instance cost for record support; keep the public
surface small.

Success means a record-shaped `deftype*` gives a value class on a supported
JVM and an identity class under fallback policy. In both representations, the
compiler-generated constructors and `create` work with no change.

## Context and scope

The Clojure compiler emits record code for a `deftype*` when its last field
has one of the names `__meta`, `__extmap`, `__hash` and `__hasheq`. It
counts the trailing fields that have these names, but the code that it
generates assumes all four, in that order, with `int` hash fields. This
design calls a form that ends with these four fields, in that order, the
record shape;
[D2](deftype-star-design/decisions.md#d2-identify-the-record-shape-by-the-compiler-rule-data-model)
gives the exact rule. For that shape the compiler emits, in addition to the
main constructor:

- a constructor with only the declared fields, which calls
  `this(fields…, null, null, 0, 0)`;
- a constructor with the declared fields, meta and extmap, which calls
  `this(fields…, meta, extmap, 0, 0)`;
- a static `create(IPersistentMap)`, which calls the main constructor with
  `null, extmap, 0, 0`.

The reader uses `create` and these constructors for `#ns.Name{…}` and
`#ns.Name[…]`. The compiler uses `create` to emit a record as a code constant.
All three pass `int` zeros for the two hash arguments. Thus the main
constructor must accept `(declared…, Object, Object, int, int)`.

`defrecord` and closed-record make `__hash` and `__hasheq` mutable caches.
Value-class fields are final, so a value record cannot cache hashes. The
fields then have no use, but the constructor parameters are necessary.

`clojure.core/deftype` rejects the four hidden names. Thus a record-shaped
type cannot go through `defvaluetype`; it needs a `deftype*` entry point.

The `defvaluetype` value transform (`->value-class`) requires exactly one
constructor and rewrites every constructor, so the record shape has a separate
value path (`->record-value-class`). For non-nil fields, the identity backend
requires each constructor to write each enabled field, but the record
delegating constructors write no fields. Thus identity output needs a rule for
delegating constructors
([D8](deftype-star-design/decisions.md#d8-enforce-non-nil-fields-in-the-main-constructor-behaviour)).

Included:

- the plain `deftype*` shape (one constructor);
- the record shape, as described above, with immutable `^int` hash fields;
- value output and identity fallback under existing policy;
- `:non-nil` fields in both shapes, under the
  [non-nil field design](non-nil-field-design.md)
  ([D6](deftype-star-design/decisions.md#d6-support-non-nil-fields-as-defvaluetype-does-scope),
  D8);
- source compilation, receiving compilation of the fallback expansion, and
  AOT, with the existing `defvaluetype` limits. As for `defvaluetype`, a
  value expansion without enabled non-nil fields evaluated apart from its
  macroexpansion fails with `:not-installed` or `:not-value-class`. An
  expansion with enabled fields keeps receiving analysis (non-nil D5).

Excluded:

- `:non-nil true` on a hidden-name field (D6);
- hidden field names outside the exact record suffix of
  [D2](deftype-star-design/decisions.md#d2-identify-the-record-shape-by-the-compiler-rule-data-model);
- mutable fields, as in `defvaluetype`;
- emulation of value equality under fallback.

## Public contract

```clojure
(value-type.core/defvaluetype* tagname classname [fields*]
  :implements [interfaces*]
  options*
  methods*)
```

The arguments are the same as for the `deftype*` special form. The expansion
evaluates to the defined class, unlike `deftype*`, which evaluates to nil.
Callers put the form where they
would put `deftype*`, inside the same top-level form, and not inside a method
body.

For a record-shaped form, the caller must:

- give `__hash` and `__hasheq` an `int` tag and no mutable metadata, and
  give `__meta` and `__extmap` no tag;
- never read or write `__hash` or `__hasheq`;
- pass `0` for the two hash arguments in explicit constructor calls.

Value output removes the `__hash` and `__hasheq` fields. Constructors keep
their descriptors and ignore the two hash arguments. Identity output keeps
both fields; they hold 0 when callers meet these obligations. value-type
does not validate the hash arguments. Callers must work with both outputs.

`:non-nil` field metadata has the contract of the
[non-nil field design](non-nil-field-design.md). In a record shape, the main
constructor enforces it. Thus the delegating constructors, `create`, both
record literal forms, record code constants and an `assoc` that the caller
implements with a constructor call (as `defrecord` does) reject `nil` for an
enabled field with `NullPointerException`, and a value of the wrong type with
`ClassCastException`. `create` from a map with no entry for an enabled field
fails. The compiler, class initialization and reflection can wrap these
exceptions. Error text is not specified.

Errors use the existing `ex-info` keys of `defvaluetype`, with `:class-name`:

| Key | When | Data |
|---|---|---|
| `:mutable-field` | a field is mutable | `:fields` |
| `:invalid-field` | a form is neither the plain nor the record shape (D2) | `:field`, `:reason :record-shape` |
| `:invalid-field` | field metadata contains `:nil-restricted`, with any value | `:field`, `:value`, `:reason :removed-nil-restricted` |
| `:invalid-field` | the `:non-nil` value is not `true`, `false` or `nil` | as for `defvaluetype`, `:reason :invalid-non-nil` |
| `:invalid-field` | `:non-nil true` on a hidden-name field (D6) | `:field`, `:reason :hidden-field` |
| `:invalid-field` | an enabled field has a missing, unresolved or ineligible tag | as for `defvaluetype` (non-nil design) |
| `:invalid-field` | in value output, a captured class accesses a removed field (D4) | `:field`, `:reason :removed-field-access` |
| `:unexpected-constructor` | the constructors are not the plain or record shape | — |

The declaration checks run at macroexpansion, before representation policy,
in this order: `:mutable-field` (including the hash fields), `:record-shape`,
metadata keys, `:hidden-field`, then tag and type reasons (D6). The first
failure gives the error. The policy, capability, receiving, loader
(`:field-class-mismatch`, `:jvm-rejected`), identity backend
(`:unsupported-shape`) and run-time check errors of `defvaluetype` also apply.

## Architectural decisions

[Decisions](deftype-star-design/decisions.md) give the choices, rejected
alternatives and rationale:

- D1: add `defvaluetype*` with `deftype*` syntax.
- D2: identify the record shape by the compiler rule, and reject partial forms.
- D3: keep the compiler-generated constructors and `create`; rewrite only the
  main constructor.
- D4: remove the hash fields from value output; keep the constructor parameters.
- D5: use the same representation policy and fallback as `defvaluetype`.
- D6: support `:non-nil` fields as `defvaluetype` does.
- D7: verification.
- D8: enforce non-nil fields in the main constructor.

## Embodiment

Implemented, including D6, D8 and the D7 non-nil gate. The elements:

- `value-type.core`: `defvaluetype*` runs the D6 declaration checks at
  macroexpansion (`:mutable-field`, `:record-shape`, metadata keys,
  `:hidden-field`, tag and type). With enabled fields, it expands to the
  receiving macros `value-field-deftype*` or `identity-field-deftype*`.
- `value-type.impl.fields`: the one owner of the non-nil declaration rules,
  including `:hidden-field`.
- `value-type.impl.transform` and `value-type.impl.value-backend`: record
  value output with field storage and checks in the main constructor only.
- `value-type.impl.identity-backend`: the D8 delegation rule.
- `value-type.impl.runtime`: `guarded-deftype*` for forms without enabled
  fields.

[Implementation detail](deftype-star-design/embodiment.md). Gate records:
[D3 feasibility gate](deftype-star-design/feasibility-gate.md) and
[non-nil gate](deftype-star-design/non-nil-gate.md).

## Qualities and risks

- Compatibility: record literals, constants and `create` work in both
  representations. Coupling to the record bytecode of `Compiler.java`
  increases. The design checks the exact shape and fails if it changes.
- Cost: value records compute hashes on each call. Removing the fields
  prevents 8 unused bytes per instance. Identity fallback keeps them.
- Observed: Valhalla `27-jep401ea3+1-1` with preview and verification
  accepts a value-class constructor that calls `this(…)`. All three
  constructors, `create`, `getBasis`, both record literals and compiled
  record constants work in both representations. Other JVMs are not verified.
  The [non-nil gate](deftype-star-design/non-nil-gate.md) adds one native and
  one generated field on the same JVM with `-Xverify:all`; it used a
  spike-local transform, and the test suites cover the production transform.
- Risk: records with enabled non-nil fields reject `nil` in `create`,
  record literals and `assoc`, where records usually accept it. Callers that
  build records from partial maps must supply each enabled field.
- Risk: records implement `java.io.Serializable`. The behavior of serializable
  value classes on the EA JVM is not verified. This design does not promise
  serialization.
- Risk: closed-record supports Clojure 1.11. value-type tests only
  Clojure 1.12.6. A caller that uses value output must use a Clojure version
  that value-type supports.
