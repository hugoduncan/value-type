# Packaged-source acceptance audit

## Verified boundary

`test/build/build_test.clj` builds the source JAR in an isolated project. Its
`isolated-consumer-test` resolves runtime dependencies from the packaged POM with
root and user dependency files disabled. Each consumer classpath contains only
the artifact and runtime JARs, not repository source or build-test paths.

`test/resources/build/isolated-consumer.clj` checks that core and array resources
come from that artifact. It requires both public namespaces before defining types.
Java 8 and preview-disabled EA3 therefore exercise public loading and generated
identity construction without optional value APIs. Preview-enabled EA3 exercises
value construction. All consumers use silent policy.

The public Segment declaration constrains a Point and a String. Direct Clojure
constructors and factories retain both concrete storage Classes and valid field
values. Each constrained position rejects nil and wrong types independently.
The Point control retains primitive field reads and representation-specific equality.

Array controls run before type creation. Unsupported configurations retain structured
allocation and inspection errors and do not consume sequence input. Supported array
allocation, inspection and sequence construction retain their explicit preview and
internal export flags. An additional preview-disabled inspection configuration tests
ordinary arrays with the export enabled.

## Verification

On 2026-10-27, ran:

```sh
JAVA_CMD="$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java" \
  clojure -M:build-test -m scry.cli --dir test/build
```

Exit status: 0. Scry: 9 tests, 110 passing assertions, zero failures or errors.
The failure directory `.scry-results` was empty. Children use EA3 from the host
Java home and Java 8 at
`/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`.

## Limits

This closes the isolated source-JAR consumer item in slice 6. It does not close
all-policy packaging, controlled native absence, source-free AOT or full runtime
acceptance. The preview-enabled packaged consumer uses the array export; separate
field-only tests establish field behavior without exports. Reflective and Java
constructor evidence belongs to the separate construction and AOT fixtures.
No new tests or mocks were needed to reconcile this item.
