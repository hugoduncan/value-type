# deftype* context and scope

The Clojure compiler emits record code for a `deftype*` when its last field
has one of the names `__meta`, `__extmap`, `__hash` and `__hasheq`. It
counts the trailing fields that have these names, but the code that it
generates assumes all four, in that order, with `int` hash fields. This
design calls a form that ends with these four fields, in that order, the
record shape;
[D2](decisions/interface-and-shape.md#d2-identify-the-record-shape-by-the-compiler-rule-data-model)
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

`create` passes `null` for `__meta` and `RT.seqOrElse(rest)` for `__extmap`,
where `rest` is the input map without the declared keys. Thus the `__extmap`
argument is nil unless the map has an undeclared key. The constructor with
only the declared fields passes nil for both. A compiled record constant
calls `create` with the map view of the instance. (Observed: `Compiler.java`
8803–8855 and `emitValue`, Clojure 1.12.1.)

`__meta` and `__extmap` are references. With compressed references they take
64 bits. On Valhalla `27-jep401ea3+1-1`, a null-restricted atomic array is
flat only when the instance fields fit in 64 bits. Thus a value record that
keeps both fields does not flatten, also with one `long` field; a
`defvaluetype` with one `long` field does
([D10](decisions/closed-record-shape.md#d10-closed-record-shape-no-metadata-and-no-extension-map-state-and-behaviour)).

`clojure.core/deftype` rejects the four hidden names. Thus a record-shaped
type cannot go through `defvaluetype`; it needs a `deftype*` entry point.

The `defvaluetype` value transform (`->value-class`) requires exactly one
constructor and rewrites every constructor, so the record shape has a separate
value path (`->record-value-class`). For non-nil fields, the identity backend
requires each constructor to write each enabled field, but the record
delegating constructors write no fields. Thus identity output needs a rule for
delegating constructors
([D8](decisions/policy-and-non-nil.md#d8-enforce-non-nil-fields-in-the-main-constructor-behaviour)).

Included:

- the plain `deftype*` shape (one constructor);
- the record shape, as described above, with immutable `^int` hash fields;
- the closed record shape: the record shape with the option
  `:value-type/closed true` (D9, D10);
- value output and identity fallback under existing policy;
- `:non-nil` fields in both shapes, under the
  [non-nil field design](../non-nil-field-design.md)
  ([D6](decisions/policy-and-non-nil.md#d6-support-non-nil-fields-as-defvaluetype-does-scope),
  D8);
- source compilation, receiving compilation of the fallback expansion, and
  AOT, with the existing `defvaluetype` limits. As for `defvaluetype`, a
  value expansion without enabled non-nil fields and without the closed
  shape, evaluated apart from its macroexpansion, fails with
  `:not-installed` or `:not-value-class`. An expansion with enabled fields
  or the closed shape keeps receiving analysis (non-nil D5, D10).

Excluded:

- `:non-nil true` on a hidden-name field (D6);
- hidden field names outside the exact record suffix of
  [D2](decisions/interface-and-shape.md#d2-identify-the-record-shape-by-the-compiler-rule-data-model);
- mutable fields, as in `defvaluetype`;
- emulation of value equality under fallback.
