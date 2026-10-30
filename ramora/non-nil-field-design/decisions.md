# Non-nil field decisions

## D1. Name the constraint, not the mechanism

Choose `:non-nil`, with standard `:tag`. It names the excluded Clojure value and
works with native or generated enforcement. Reject `:required` (can imply argument
presence or defaults), `:non-null` (Java terminology) and retaining `:nil-restricted`
(too closely tied to the native-only contract). No compatibility alias is required.
Reject the old key explicitly rather than silently treating it as unknown metadata.
The naming choice is firm for this design; the [parent design](../non-nil-field-design.md#embodiment-and-observed-qualities)
records the implemented contract and accepted evidence limits.

## D2. Separate representation from enforcement

Select owner representation through existing capability and application policy.
Then select native or generated enforcement per enabled field. Native absence is
not identity fallback and causes no fallback warning or strict-policy error.
Keep the lazy native probe separate; it must demonstrate valid construction and
native nil rejection. Do not retry defective transformations with another backend.
This preserves application authority without making field constraints optional.

## D3. Share field specifications, isolate bytecode backends

Core validates declarations. Normalized specifications carry exact Classes
and enforcement choices through receiver-local capture state. Both
backends retype storage, cast constructor inputs and rewrite matching owner and
companion field references. No global registry is needed.

Keep `java.lang.classfile` for value-class output. Use a baseline-safe bytecode
backend for identity output, preserving its ordinary class version and constructor
ordering. Use Clojure's bundled ASM for identity output. The baseline-JVM
experiment verified constructor, companion and AOT behavior. This adds maintenance cost but avoids a new fallback
JDK requirement. A shared field model limits semantic drift between backends.

## D4. Put generated checks in constructors

For generated enforcement, cast and reject nil before writing each enabled field.
Native fields use the JVM restriction plus the cast. Preserve representation-specific
initialization rules and validate compiler output shapes before transformation.
Factory-only checks and unchecked ordinary deftype fallback are rejected: Java and
reflection must not bypass the invariant. Generated construction cost is accepted;
no per-read capability or policy check is added.

## D5. Preserve deployment boundaries

Selection validates declarations and policy. Transferred expansions retain receiving
analysis that resolves exact Classes, verifies the selected modes and installs a
local capture loader. Evaluation retains applicable representation-policy guards.
AOT writes the transformed bytes for both representations. Representation and field
enforcement are fixed in compiled artifacts; receivers neither upgrade nor downgrade
them. Value output requires a compatible JVM. Identity output follows existing AOT
fallback policy even on a capable JVM. No load-time conversion is promised.

