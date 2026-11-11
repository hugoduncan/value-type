# Release embodiment

The [release workflow](../release-workflow.md) uses source-only packaging,
checked JVM provisioning and separate preparation, preview and publication.
No release is published. Live publication and its permissions remain unverified.

## Verification scope

- Pure tests cover changelog validation, stamping and note selection.
- Isolated real Git/process tests cover two-file preparation, first and
  replacement README coordinates, selected commit identity, clean final state,
  output preservation and partial failures.
- Credential tests reject absent or blank values before effects. Valid values
  are checked without uploads. Tests perform no publication writes.
- Artifact tests check POM-derived classpaths and exact-JAR resources.
- Runtime JVMs use the [JVM facts](../../dev/jvm/jvms.edn). The full matrix
  uses the baseline and Valhalla pair. The ordinary role is smoke-only:
  loading, definition policy and a same-JDK source-free AOT round trip.
- Forced JIT covers the runtime suite parent, including AOT production.
  Runtime AOT consumers and probe children use normal compilation.
- Local preview coverage uses native Linux ARM64. Hosted CI targets Linux
  x64. Local ARM64 results do not establish x64 behavior, hosted permissions
  or successful publication.

Publication is not atomic. Command success does not independently verify
remote files. See the [operator guide](../../doc/development/release.md)
for inspection and recovery constraints.

Detailed commands and results are retained in the
[release acceptance evidence](../../munera/closed/027-jvm-facts-test-tiers-and-releases/implementation.md#five-criterion-acceptance-map)
and [JVM verification record](../../munera/closed/011-release-workflow/linux-jvms.md).
