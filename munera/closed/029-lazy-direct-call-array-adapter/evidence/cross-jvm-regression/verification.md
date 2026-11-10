# Cross-JVM regression verification

Tested revision: `67b9aafda5866d7349909850a759889cabc3018f`.
No source or test changes were needed for this slice.

Command from the repository root:

```sh
export VALUE_TYPE_BASELINE_JAVA="$HOME/.cache/value-type/jvms/baseline/1.8.0_492-b09/macos-aarch64/Contents/Home/bin/java"
export VALUE_TYPE_VALHALLA_JAVA="$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java"
export JAVA_CMD="$VALUE_TYPE_BASELINE_JAVA"
export VALUE_TYPE_TEST_RECORD="$PWD/munera/open/029-lazy-direct-call-array-adapter/evidence/cross-jvm-regression/record.edn"
clojure -M:cross-jvm-test \
  --namespace value-type.array-info-boundary-test \
  --namespace value-type.array-loading-test \
  --namespace value-type.array-boundary-test \
  --namespace value-type.array-matrix-test \
  --namespace value-type.array-sequence-boundary-test \
  --namespace value-type.capability-matrix-test \
  --namespace value-type.value-class-switch-test
```

Result: exit 0; 23 tests and 961 assertions passed. `.scry-results/` had no
failure files. `record.edn` retains all 23 Var records, 961 assertion labels,
239 child starts and 227 cells. `run.log` retains the Scry summary.

The parent uses Java 8 with no preview or export options. Facts validation
checks Java 8 `1.8.0_492-b09` and EA3 `27-jep401ea3+1-1`. Child tests check
actual runtime builds and input arguments against their configuration. Array
rows include Java 8 plain and EA3 plain, preview, export, preview/export,
and preview/export with `-XX:-UseArrayFlattening`. Policy and switch tests
add their per-child system properties. Require/AOT checks cover the five
specified profiles and the plain-producer to preview/export-receiver case.

The label recorder stores configuration hashes, not raw child output or
argument vectors. Passing assertions establish argument agreement; do not
interpret the record as a raw JVM transcript. Exact expected flags remain in
the test configuration and facts at the tested revision.

These are real JVM checks without mocks. This run is not the full cross-JVM
suite, isolated JAR verification, or a performance measurement.
