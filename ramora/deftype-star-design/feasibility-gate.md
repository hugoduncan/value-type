# deftype* feasibility gate (D3/D7)

Environment: Valhalla `27-jep401ea3+1-1`, Clojure `1.12.6`, macOS aarch64,
`--enable-preview -Xverify:all`. The result applies to this build and this
fixture only.

[Reproduction instructions](../../spike/record-value-type/README.md).
[Raw result](evidence/gate-ea3-preview.edn). Source hash of
`spike/record-value-type/gate.clj` at the run (SHA-256):
`42d2284d1f8be4151c6a40c729db4b03d32074ac5971dd2d617b3685a80e2457`.

## Method

A spike-local transform changes the record-shaped class: version with the
preview minor version, no `ACC_IDENTITY`, `ACC_STRICT_INIT` on kept fields,
hash fields removed, main constructor body replaced by kept-field writes
(original slots) and then `Object.<init>`. The delegating constructors and
`create` are not changed, so they call `this(…)` with `0, 0` for the hash
arguments.

## Observations

- The JVM defines and links the class with full verification. `Class::isValue`
  returns true; no declared field is named `__hash` or `__hasheq`; there are
  three constructors.
- The main constructor, both delegating constructors, `create`, the map
  literal, the positional literal, the compiled record constant and
  `withMeta` give instances with the expected declared fields, extmap
  entries and metadata.
- `=` of two instances built from equal arguments returns true.

## Limits

Serialization, preview off, Java 8 and the production transform are not
covered here. Slice 3 and Slice 5 tests cover the production path.
