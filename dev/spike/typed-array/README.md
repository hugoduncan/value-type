# Typed-array eligibility experiment

`eligibility.clj` compares flattening for one-long and two-long value classes
in ordinary, null-restricted atomic and null-restricted non-atomic arrays.
It calls the experimental JVM API directly. Results depend on the JVM build.

Run from the repository root with the Valhalla EA3 JDK:

```sh
JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java \
  clojure -J--add-exports=java.base/jdk.internal.value=ALL-UNNAMED \
  -M:object-size dev/spike/typed-array/eligibility.clj
```

The repeatable storage, allocation and traversal benchmark is now in
[`dev/bench/typed-array/`](../../bench/typed-array/README.md). It uses the public
array allocator. Historical results remain in the
[typed-array report](../../../ramora/defvaluetype-design/typed-array.md).
