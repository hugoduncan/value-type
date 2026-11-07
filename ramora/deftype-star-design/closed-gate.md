# deftype* closed gate (D7)

Environment: Valhalla `27-jep401ea3+1-1`, Clojure `1.12.6`, macOS aarch64,
`--enable-preview -Xverify:all`,
`--add-exports=java.base/jdk.internal.value=ALL-UNNAMED`. The result
applies to this build and this fixture only.

[Reproduction instructions](../../dev/spike/record-value-type/README.md#closed-gate).
[Raw result](evidence/closed-gate-ea3-preview.edn). Source hash of
`dev/spike/record-value-type/closed-gate.clj` at the run (SHA-256):
`8bd5bcdcdb3d2be9d4f6bd61f80a3eee654f823567858571d03a63c7d2f53e29`.

## Method

A spike-local transform changes a record-shaped class with the fields
`[^long x __meta __extmap ^int __hash ^int __hasheq]`: version with the
preview minor version, no `ACC_IDENTITY`, `ACC_STRICT_INIT` on the kept
field, all four hidden fields removed. Only the main constructor body
changes. It starts with the D10 check, for `__meta` and then `__extmap`:
`aload`, `ifnull`, then `new`, `dup`, `ldc`, `invokespecial` and `athrow` of
`IllegalArgumentException(String)`. Then it writes `x` and calls
`Object.<init>`. The code is emitted with `CodeBuilder`;
`java.lang.classfile` generates the stack map frames. The check adds the
first stack map frames in a value-class constructor. The delegating
constructors and `create` do not change. The class body never reads or
writes `__meta` or `__extmap`.

## Observations

- The JVM defines and links the class with full verification.
  `Class::isValue` is true; the only instance field is `x`; there are three
  constructors.
- Valid arguments construct through the main constructor and both
  delegating constructors (direct and by reflection), `create`, the map
  literal, the positional literal and a compiled record constant.
- A non-nil `__meta` or `__extmap` argument to the main constructor or to
  the meta-and-extmap constructor (direct and by reflection) gives
  `IllegalArgumentException`. `create` and the map literal with an
  undeclared key (`load-string` and `read-string`) give
  `IllegalArgumentException`. Wrappers: reflection gives
  `InvocationTargetException`, `load-string` gives `CompilerException`.
- `ValueClass.isFlatArray` is true for a null-restricted atomic array of the
  class (`ValueClass.newNullRestrictedAtomicArray`).

## Limits

The production transform is not covered. Preview off, Java 8, identity
output and serialization are not covered by this gate. Flattening is
evidence for this build, not a contract.
