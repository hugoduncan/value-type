# Object-size cause probe

Run from the repository root:

```sh
bash spike/object-size/run.sh
```

The default JDK is `~/.local/jdks/jdk-27.jdk/Contents/Home`.
Set `JAVA_CMD` to another Valhalla `bin/java` if needed. The Java control
requires the JEP 401 preview syntax and compiles with `--release 27`.
Clojure CLI and the project's `:object-size` dependencies are required.

The script runs three fresh-JVM configurations: preview defaults,
`-XX:-UseNullableNonAtomicValueFlattening`, then that flag combined with
`-XX:-UseNullableValueFlattening`. For each, it:

- Prints the benchmark classes' VM layouts without a measurement agent.
- Runs the existing Criterium allocation measurement and validation.
- Measures plain Java controls with a small Java instrumentation agent
  and prints their VM layouts.

Output is in `target/object-size-investigation/`. The script also overwrites
`target/object-size/raw.edn` and `results.edn` through the existing runner.
It does not change the original evidence under `ramora/`.

The Java agent is built locally from `SizeControl.java`; it does not use
Criterium. Layout extracts retain complete target-class blocks. Full logs
remain in `target/`. Sizes are observations, not portable test expectations.

See [findings](../../ramora/defvaluetype-design/object-size-cause.md).
