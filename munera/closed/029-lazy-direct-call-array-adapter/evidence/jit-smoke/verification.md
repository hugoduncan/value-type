# Forced-JIT and ordinary-JDK smoke verification

Revision: `235048c9f36fecaf9b6803dfd7725c0dcc816056`.
Host: macOS ARM64. Clojure: 1.12.6.

Commands from the repository root:

```sh
VALUE_TYPE_VALHALLA_JAVA="$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java" bb test:forced-jit
JAVA_CMD="$HOME/.cache/value-type/jvms/baseline/1.8.0_492-b09/macos-aarch64/Contents/Home/bin/java" clojure -M:smoke-test
```

| Gate | Tests | Assertions | Exit |
| --- | ---: | ---: | ---: |
| Forced-JIT runtime | 191 | 5238 | 0 |
| Ordinary-JDK smoke | 3 | 45 | 0 |

Both runs have zero failures and errors. Adjacent logs retain summaries;
`.exit` files retain process status. Each Scry failure directory was copied
before the next run and checked: both were empty. Git does not retain empty
directories. No failure EDN files were produced.

The forced-JIT runner verifies EA3 build `27-jep401ea3+1-1`. Its log records
preview, exports, explicit auto and `-Xcomp -Xbatch -XX:-TieredCompilation`
for the Scry parent. Duration was 25.11 seconds. Forced compilation covers
the runtime parent and AOT production, not its normal preview-only children.

The smoke parent is Java 8 `1.8.0_492-b09` (x64 through Rosetta). Children
use the verified ordinary role, `25.0.5+8-LTS`, with explicit auto and no
preview or exports. The suite checks startup, definition policies and
same-JDK source-free AOT. This is smoke coverage, not a full ordinary-JDK
matrix or forced-JIT evidence. No raw child argument transcript was retained.

No production, test or dependency changes were needed. Architecture updates,
quality checks and the final acceptance audit remain open.
