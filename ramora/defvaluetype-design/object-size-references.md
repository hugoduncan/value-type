# Object reference fields

## Result

The default size difference is also +8 bytes for 1–3 `Object` reference
fields on Valhalla `27-jep401ea3+1-1`, macOS/aarch64. The detailed layout
and response to VM flags differ from the [long-field cases](object-size-cause.md).
Compressed references occupy 4 bytes; object alignment is 8 bytes.

| Reference fields | `deftype` | `defvaluetype`, default | Non-atomic nullable off | Both nullable options off |
| ---: | ---: | ---: | ---: | ---: |
| 1 | 16 | 24 | 24 | 16 |
| 2 | 24 | 32 | 24 | 24 |
| 3 | 24 | 32 | 24 | 24 |

All sizes are shallow bytes. Referenced objects are excluded. Identity
sizes stay unchanged across these configurations. Null and non-null field
values give the same results. These are ordinary reference fields, not
fields declared with a specific value-class type.

The non-atomic option is `-XX:-UseNullableNonAtomicValueFlattening`.
The third configuration also sets `-XX:-UseNullableValueFlattening`,
which disables atomic nullable layouts.

## Layout explains the difference

- **One reference:** the identity field starts at offset 12. The value
  field starts at 16, followed by a null marker at 20. Its nullable atomic
  payload is 8 bytes, aligned to 8 bytes. The extra space comprises a
  4-byte gap before the field, the marker, and 3 bytes after the marker.
  Disabling only non-atomic nullable layouts leaves the atomic nullable
  layout and its marker intact.
- **Two references:** identity fields start at 12 and 16. Value fields
  start at 16 and 20, with a marker at 24. The null-free atomic layout
  requires 8-byte alignment. The 9-byte nullable payload cannot use an
  atomic layout on this VM. Disabling non-atomic nullable layouts removes
  the marker, reducing total size to 24 bytes. Field offsets still differ
  from the identity class, despite equal total sizes.
- **Three references:** both classes have fields at 12, 16 and 20. The
  value class adds a marker at 24, followed by 7 bytes of end padding.
  Neither atomic layout is available. Disabling non-atomic nullable
  layouts removes the marker and the size difference.

Thus the +8-byte default difference is the same in these cases, but it
is not always just one marker plus seven end-padding bytes. Payload
alignment also matters. Do not generalize this table to other numbers
or types of fields, uncompressed references, or another JDK build.

## Verification and reproduction

`bash spike/object-size/run.sh` now runs all three configurations. It
measures Clojure `deftype`/`defvaluetype` pairs and plain Java identity/value
pairs through the locally built instrumentation agent. VM layout reports
agree with `Instrumentation.getObjectSize` in each case. Each reference
class is measured with null fields and with non-null fields. The existing
Criterium long-field checks also pass in all three configurations.

Sources: `spike/object-size/references.clj` and
`spike/object-size/SizeControl.java`.
Retained [reference evidence](object-size-investigation/references/) contains
complete target-class layouts and measurement rows for Clojure and Java,
under `default/`, `no-nullable-non-atomic/` and `no-nullable-layouts/`.
The Java rows list non-null reference cases first, then null cases.
The build and default flags are recorded in the parent investigation directory.
Production code is unchanged.
