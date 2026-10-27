# Array safety constraints

These constraints apply to the [array design](../value-type-array-design.md).

- Reject null before native diagnostics. Primitive and non-array rejection is
  public validation, not native behavior. Test the wrapper without crashing
  the shared test JVM.
- Separate discovery, invocation access and value-allocation prerequisites.
  Preview-off and Java 8 dependent allocations in the direct probe are
  unresolved because strict value fixtures cannot load; they are not native
  allocator rejections.
- Reject primitive components independently of value status. Do not promise
  eligibility for untested runtime classes or require flat storage.
- Test namespace loading, capability errors, sequence realization, public
  length validation, fallback-policy independence and packaged consumers.
  Direct probes alone do not verify public library paths.
- Keep concurrent access and copy guarantees separate from sequential store
  checks. Do not infer broader JDK support from one tested build.

See [verification](verification.md) for required checks and
[embodiment](embodiment.md#verification-scope-and-gaps) for implementation
coverage and gaps.
