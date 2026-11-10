# Runtime regression

Revision: `79d1961728a9809b61f5322c8a40d7b306260dda`.
Host: Darwin ARM64. Clojure: 1.12.6. JVM: `27-jep401ea3+1-1`.

From the repository root:

```sh
export JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java
export VALUE_TYPE_VALHALLA_JAVA=$JAVA_CMD
clojure -J--add-exports=java.base/jdk.internal.value=ALL-UNNAMED -M:test -m scry.cli --dir test/runtime
for setting in + -; do
  clojure -J--add-exports=java.base/jdk.internal.value=ALL-UNNAMED -J-XX:${setting}UseArrayFlattening -M:test -m scry.cli --namespace value-type.array-test --namespace value-type.array-layout-test --namespace value-type.array-sequence-test --namespace value-type.array-info-test
done
```

The `:test` alias adds `--enable-preview`. The full suite uses default
flattening. The focused runs set flattening explicitly in the parent JVM.

| Run | Tests | Assertions | Exit |
| --- | ---: | ---: | ---: |
| Full runtime | 191 | 5234 | 0 |
| Array controls, flattening enabled | 26 | 2665 | 0 |
| Array controls, flattening disabled | 26 | 2788 | 0 |

All runs have zero failures and errors. Each run's `.scry-results/` was
copied before the next run; all three directories were empty (Git does not
retain empty directories). Adjacent `.log` and `.exit` files retain CLI
summaries and process status. No failure EDN files were produced.

These tests use real arrays and an independent JDK diagnostic oracle. Existing
controlled capability tests in the full suite are controlled evidence, not
proof of JVM support. The earlier disposable diagnostic-input checks remain
the invalid-input safety evidence. These runs do not establish cross-JVM,
fallback, forced-JIT, smoke or quality-gate completion.
