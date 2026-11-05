# deftype* non-nil gate (D7)

Environment: Valhalla `27-jep401ea3+1-1`, Clojure `1.12.6`, macOS aarch64,
`--enable-preview -Xverify:all`. The result applies to this build and this
fixture only.

[Reproduction instructions](../../spike/record-value-type/README.md#non-nil-gate).
[Raw result](evidence/non-nil-gate-ea3-preview.edn). Source hash of
`spike/record-value-type/non-nil-gate.clj` at the run (SHA-256):
`bcea76312b49271165503e9412b135ca43520dd8e2b2e88321bb91a77cb613d7`.

## Method

A spike-local transform changes a record-shaped class with the fields
`[p s __meta __extmap ^int __hash ^int __hasheq]`: version with the preview
minor version, no `ACC_IDENTITY`, `ACC_STRICT_INIT` on kept fields, hash
fields removed. Field `p` has storage type `Pt` (a value class) with
`NullRestricted` (native). Field `s` has storage type `String` (generated).
Only the main constructor body changes: kept-field writes from the original
slots, `checkcast` for both fields, `Objects.requireNonNull` before the
`String` cast, then `Object.<init>`. Owner field references to `p` and `s`
use the concrete descriptors. The delegating constructors and `create` do
not change.

## Observations

- The JVM defines and links the class with full verification. `Class::isValue`
  is true; there are three constructors; `p` has type `Pt` and
  `NullRestricted`, `s` has type `String`; there are no hash fields.
- Valid arguments construct through the main constructor, both delegating
  constructors (direct and by reflection), `create`, the map literal, the
  positional literal (`load-string` and `read-string`) and a compiled record
  constant. The fields read back with the given values.
- On each of these paths, `nil` for `p` or `s` gives
  `NullPointerException`, and a wrong-type value gives
  `ClassCastException`, in the cause chain. Wrappers: reflection gives
  `InvocationTargetException`, `load-string` gives `CompilerException`, the
  compiled constant gives `ExceptionInInitializerError`, direct calls,
  `create` and `read-string` give the exception directly.
- `create` without the `:p` key gives `NullPointerException`.
- The compiled constant cases use a valid instance with an `__extmap` that
  gives the rejected values in the map view; the compiler emits a call to
  `create` with that view.

## Limits

The production transform is not covered. Preview off, Java 8, identity
output and serialization are not covered by this gate. The runtime and
fallback suites cover the production transform.
