# deftype* public contract

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

## Closed record shape

The option `:value-type/closed true`, in the `deftype*` option position,
selects the closed record shape (D9). `false`, `nil` and an absent option
do not select it; the form keeps the plain or record shape. The option
requires the record shape. When the option occurs more than once, the last
occurrence applies, as in `deftype*`. `defvaluetype*` removes each
occurrence of the option from the `deftype*` call.

For a closed form, the caller must also:

- never read or write `__meta` or `__extmap`;
- pass nil for both in explicit constructor calls;
- not give the type metadata or extension keys through these fields, for
  example with `withMeta` or `assoc` methods that use them.

value-type then (D10):

- removes `__meta`, `__extmap`, `__hash` and `__hasheq` from value output.
  Constructors keep their descriptors;
- keeps all four fields in identity output. `__meta` and `__extmap` hold nil;
- in both outputs, makes the main constructor throw
  `IllegalArgumentException` for a non-nil `__meta` or `__extmap` argument,
  before the non-nil field checks. Thus `create` and the map literal reject
  an undeclared key. The positional literal, the constructor with only the
  declared fields and compiled record constants are not affected. The
  compiler, the reader and reflection can wrap the exception. Error text is
  not specified.

## Non-nil fields

`:non-nil` field metadata has the contract of the
[non-nil field design](../non-nil-field-design.md). In a record shape, the
main constructor enforces it. Thus the delegating constructors, `create`, both
record literal forms, record code constants and an `assoc` that the caller
implements with a constructor call (as `defrecord` does) reject `nil` for an
enabled field with `NullPointerException`, and a value of the wrong type with
`ClassCastException`. `create` from a map with no entry for an enabled field
fails. The compiler, class initialization and reflection can wrap these
exceptions. Error text is not specified.

## Errors

Errors use the existing `ex-info` keys of `defvaluetype`, with `:class-name`:

| Key | When | Data |
|---|---|---|
| `:mutable-field` | a field is mutable | `:fields` |
| `:invalid-field` | a form is neither the plain nor the record shape (D2) | `:field`, `:reason :record-shape` |
| `:invalid-option` | the `:value-type/closed` value is not `true`, `false` or `nil` (D9) | `:option`, `:value`, `:reason :invalid-value` |
| `:invalid-option` | `:value-type/closed true` on a plain-shape form (D9) | `:option`, `:value`, `:reason :record-shape-required` |
| `:invalid-field` | field metadata contains `:nil-restricted`, with any value | `:field`, `:value`, `:reason :removed-nil-restricted` |
| `:invalid-field` | the `:non-nil` value is not `true`, `false` or `nil` | as for `defvaluetype`, `:reason :invalid-non-nil` |
| `:invalid-field` | `:non-nil true` on a hidden-name field (D6) | `:field`, `:reason :hidden-field` |
| `:invalid-field` | an enabled field has a missing, unresolved or ineligible tag | as for `defvaluetype` (non-nil design) |
| `:invalid-field` | in value output, a captured class accesses a removed field (D4, D10) | `:field`, `:reason :removed-field-access` |
| `:unexpected-constructor` | the constructors are not the plain or record shape | — |

The declaration checks run at macroexpansion, before representation policy,
in this order: `:mutable-field` (including the hash fields), `:record-shape`,
`:invalid-option` (D9), metadata keys, `:hidden-field`, then tag and type
reasons (D6). The first
failure gives the error. The policy, capability, receiving, loader
(`:field-class-mismatch`, `:jvm-rejected`), identity backend
(`:unsupported-shape`) and run-time check errors of `defvaluetype` also apply.
For a closed form, the identity backend also gives `:unsupported-shape` for
the constructor cases of D10: a non-delegating constructor with fewer than
four parameters or with a parameter at n − 4 or n − 3 that is not of type
`Object`, and a class with no non-delegating constructor. Compiler output of
a form that passes the declaration checks does not have these cases.
