# Full cross-JVM and fallback verification

Revision: `0281c6406d1ddc4dad86fbaddb14ec761ef0dceb`.
Host: macOS ARM64. Clojure: 1.12.6. Facts-selected builds: Java 8
`1.8.0_492-b09` (x64 through Rosetta) and Valhalla `27-jep401ea3+1-1`.

Commands from the repository root, in order:

```sh
export VALUE_TYPE_BASELINE_JAVA="$HOME/.cache/value-type/jvms/baseline/1.8.0_492-b09/macos-aarch64/Contents/Home/bin/java"
export VALUE_TYPE_VALHALLA_JAVA="$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java"
JAVA_CMD="$VALUE_TYPE_BASELINE_JAVA" clojure -M:cross-jvm-test
JAVA_CMD="$VALUE_TYPE_BASELINE_JAVA" clojure -M:fallback-test
JAVA_CMD="$VALUE_TYPE_VALHALLA_JAVA" clojure -M:fallback-test
JAVA_CMD="$VALUE_TYPE_VALHALLA_JAVA" clojure -J--enable-preview -M:fallback-test
```

| Run | Tests | Assertions | Exit |
| --- | ---: | ---: | ---: |
| Full cross-JVM, Java 8 parent | 121 | 5226 | 0 |
| Fallback, Java 8 parent | 78 | 2636 | 0 |
| Fallback, EA3 preview off | 78 | 2636 | 0 |
| Fallback, EA3 preview on | 78 | 2636 | 0 |

All runs have zero failures and errors. Adjacent logs retain Scry summaries;
`.exit` files retain process status. Each `.scry-results/` directory was
copied and checked before the next run. All were empty; Git does not retain
empty directories. No failure EDN files were produced.

Children use the existing facts and test configurations, not inherited parent
flags. No new mocks, flags, dependencies or tests were needed. Existing
controlled-capability tests remain controlled evidence, not JVM support proof.
This run retains no child label record or raw child argument transcript.
Earlier focused boundary evidence remains the detailed array-profile record.
Forced-JIT, ordinary-JDK smoke, architecture and quality gates remain open.
