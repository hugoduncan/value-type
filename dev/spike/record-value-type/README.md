# Record-shaped value class gate

Task 019, Slice 1 (D3/D7 feasibility gate). `gate.clj` transforms a
record-shaped `deftype*` class into a value class with a spike-local
transform. The transform rewrites only the main constructor, removes
`__hash` and `__hasheq`, and keeps the two delegating constructors and
`create` unchanged. It does not use the `value-type` loader or transform.

The fixture has the fields `[a ^long b ^double c __meta __extmap ^int __hash
^int __hasheq]`. Thus the main constructor has wide argument slots.

## Command

Run from the repository root with Valhalla `27-jep401ea3+1-1`:

```sh
JDK="$HOME/.local/jdks/jdk-27.jdk/Contents/Home"
"$JDK/bin/java" --enable-preview -Xverify:all \
  -cp "$(JAVA_CMD="$JDK/bin/java" clojure -Spath)" \
  clojure.main dev/spike/record-value-type/gate.clj
```

The script prints one EDN map (`:results`, `:failed`) and exits 0 only when
no case fails. Cases: value class, hash fields removed, constructor count,
main constructor, declared-fields constructor, meta-and-extmap constructor,
`create`, map literal `#gate.Rec{…}`, positional literal `#gate.Rec[…]`,
compiled record constant (`eval` of a form that embeds an instance),
`withMeta`, and `=` of two equal instances.

Without `--enable-preview` the JVM rejects the preview class version; that
configuration is not a gate case.

## Non-nil gate

Task 020, Slice 1 (D7 non-nil gate). `non-nil-gate.clj` transforms a
record-shaped `deftype*` class with the fields `[p s __meta __extmap ^int
__hash ^int __hasheq]` into a value class with a spike-local transform.
Field `p` has native storage (tag `Pt`, a `defvaluetype` class, with
`NullRestricted`). Field `s` has generated storage (`String`,
`Objects.requireNonNull` and `checkcast`). Only the main constructor
changes; owner field references use the concrete descriptors.
`gate.clj` does not change.

```sh
JDK="$HOME/.local/jdks/jdk-27.jdk/Contents/Home"
"$JDK/bin/java" --enable-preview -Xverify:all \
  --add-exports=java.base/jdk.internal.value=ALL-UNNAMED \
  -cp "$(JAVA_CMD="$JDK/bin/java" clojure -Spath)" \
  clojure.main dev/spike/record-value-type/non-nil-gate.clj
```

The script prints one EDN map (`:results`, `:failed`) and exits 0 only when
no case fails. Structure cases: value class, three constructors, field
storage and `NullRestricted`, `create` with an absent key, `withMeta`.
Path cases: each of the three constructors (direct and by reflection),
`create`, the map literal, the positional literal (`load-string` and
`read-string`) and a compiled record constant, each with valid arguments
and with `nil` and wrong-type values for the native and the generated
field. A rejection case passes only when the expected exception class is
in the cause chain.

## Closed gate

Task 022, Slice 1 (D7 closed gate). `closed-gate.clj` transforms a
record-shaped `deftype*` class with the fields `[^long x __meta __extmap
^int __hash ^int __hasheq]` into a value class with a spike-local
transform. The transform removes all four hidden fields and adds the D10
check at the start of the main constructor: a non-nil `__meta` or
`__extmap` argument throws `IllegalArgumentException`. `gate.clj` and
`non-nil-gate.clj` do not change.

```sh
JDK="$HOME/.local/jdks/jdk-27.jdk/Contents/Home"
"$JDK/bin/java" --enable-preview -Xverify:all \
  --add-exports=java.base/jdk.internal.value=ALL-UNNAMED \
  -cp "$(JAVA_CMD="$JDK/bin/java" clojure -Spath)" \
  clojure.main dev/spike/record-value-type/closed-gate.clj
```

The script prints one EDN map (`:results`, `:failed`) and exits 0 only when
no case fails. Structure cases: value class, single field `x`, three
constructors. Path cases: each constructor (direct and by reflection),
`create`, the map literal, the positional literal and a compiled record
constant with valid arguments. Rejection cases: non-nil `__meta` and
`__extmap` for the main and the meta-and-extmap constructor (direct and by
reflection), `create` and the map literal (`load-string` and `read-string`)
with an undeclared key. A rejection case passes only when
`IllegalArgumentException` is in the cause chain. Layout case:
`ValueClass.isFlatArray` of a null-restricted atomic array.
