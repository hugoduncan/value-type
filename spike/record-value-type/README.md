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
  clojure.main spike/record-value-type/gate.clj
```

The script prints one EDN map (`:results`, `:failed`) and exits 0 only when
no case fails. Cases: value class, hash fields removed, constructor count,
main constructor, declared-fields constructor, meta-and-extmap constructor,
`create`, map literal `#gate.Rec{…}`, positional literal `#gate.Rec[…]`,
compiled record constant (`eval` of a form that embeds an instance),
`withMeta`, and `=` of two equal instances.

Without `--enable-preview` the JVM rejects the preview class version; that
configuration is not a gate case.
