# Null-restricted field experiment

## Result

Native null-restricted fields are usable from application-defined value classes
on Valhalla `27-jep401ea3+1-1`, with Clojure 1.12.6, on macOS AArch64.
This is internal EA support, not a Java SE contract. Production code is unchanged.
The experiment was prepared with AI assistance and is not a supported API.

The full matrix in `run.sh` passed. Retained output is in `evidence/ea3.txt`.

## Run

From the repository root, with Clojure CLI and GNU `timeout` installed:

```sh
JAVA_CMD="$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java" \
  bash dev/spike/null-restricted-fields/run.sh
```

Each child process has a 60-second limit. The script checks exit status and
assertions. It removes temporary AOT output. It runs normal, diagnostic,
field-flattening-disabled, interpreter-only, rejection and AOT cases.

Core construction needs only `--enable-preview`. The diagnostic cases also use:

```text
--add-exports=java.base/jdk.internal.value=ALL-UNNAMED
--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED
```

These exports allow field restriction and layout queries. Emitting the annotation
by descriptor does not require an annotation-package export in this experiment.

## Mechanism

The installed JDK contains `jdk.internal.vm.annotation.NullRestricted`. Its source
states that fields follow strict initialization rules, reject null writes and may
use additional flattened layouts. It warns that these behaviors are not specified
by Java SE and are intended for internal JDK experiments.

Clojure preserves Java annotation metadata, but emits non-primitive `deftype`
fields as `Object`, even with a concrete value-class type hint. A raw annotation
on that field fails: EA3 requires its declared type to be a value class.

`field-probe.support/probe-type` is an experiment macro, not `defvaluetype` API.
It uses the same compiler/capture-loader approach and calls the production
`transform/->value-class`. A second transform then:

1. Changes selected reference-field descriptors from `Object` to a concrete type.
2. Emits the runtime-visible internal annotation for restricted cases.
3. Changes matching field-access descriptors in the generated class.
4. Adds a `checkcast` before each affected constructor field write.

Constructor parameters stay `Object`. This preserves Clojure's generated factory
calls. The production transform already supplies final, strict fields and
pre-super initialization. No `LoadableDescriptors` attribute was added.

The experiment replaces selected field attributes rather than preserving them.
A production transform must preserve unrelated field metadata. The prototype is
not suitable for arbitrary user classes.

## Verified behavior

For a segment with two restricted point fields:

- Non-null construction succeeds; the holder is a value class.
- Direct constructors, the generated factory and reflective construction reject
  null with `NullPointerException`, including null in the second field.
- A wrong-type constructor argument throws `ClassCastException`.
- Direct and reflective field reads work.
- Interface methods, closures inside methods and closures in external functions
  return the expected field value.
- A typed nullable control accepts null.
- A 30,000-iteration construction/read loop passes, followed by null rejection.
  This is a warmup smoke check, not proof of every JIT compilation path.
- Interpreter-only execution passes.
- Disabling field flattening preserves null rejection.
- AOT compilation passes. A fresh process reads the compiled fixtures without the
  spike source directory on its classpath and repeats the semantic checks.

Layout queries use valid, non-null `Field` instances only:

| Nested value | Restricted field | Typed nullable control |
|---|---|---|
| One `long` | Flat | Not flat |
| Two `long`s | Not flat | Not flat |

With `-XX:-UseFieldFlattening`, all four fields are non-flat. Native restriction
remains true for the restricted segment field and false for its nullable control.
No throughput, allocation-rate or retained-size benchmark was run.

## Rejection boundaries

| Case | Observed deepest cause |
|---|---|
| Raw annotation on Clojure's `Object` field | `IncompatibleClassChangeError` |
| Retyped restricted `String` field | `IncompatibleClassChangeError` |
| Annotation on primitive `long` | `ClassFormatError` |
| Restricted field of its own enclosing type | `ClassCircularityError` |
| Raw annotation through preview-disabled fallback | `ClassFormatError` |

This is not a general non-null reference feature: it cannot directly express a
required `String` or `Object` field. Those need constructor validation instead.

## Implementation recommendation

An opt-in, concrete-value-field feature is feasible on this build. Do not expose
the internal annotation as the user API. A production design should:

- Resolve explicit field types and reject unsupported declarations early.
- Carry field specifications through the loader to the transform.
- Retype fields and their accesses while retaining Object constructor parameters.
- Preserve unrelated attributes and existing constructor-shape checks.
- Probe actual field enforcement separately from basic value-class capability.
- Define unsupported-JVM behavior explicitly: reject the feature, or preserve a
  separately specified non-null contract through constructor checks. Do not pass
  the native annotation unchanged to ordinary `deftype` fallback.
- Keep flattening optional and avoid performance promises.

Remaining verification includes Java 8 fallback, other JVM builds, mutual cycles,
abstract/interface/array field types, type redefinition, broader method/control-flow
shapes, separately compiled consumers and the full compatibility corpus. The
source-free AOT fixture check is not a substitute for these tests.

The architecture note that lists null-restricted fields as future work and says
they need `LoadableDescriptors` needs review: that attribute was not required by
these selected fixtures. This does not prove it is unnecessary in every loading
scenario.
