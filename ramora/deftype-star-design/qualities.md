# deftype* qualities and risks

- Compatibility: record literals, constants and `create` work in both
  representations. Coupling to the record bytecode of `Compiler.java`
  increases. The design checks the exact shape and fails if it changes.
- Cost: value records compute hashes on each call. Removing the fields
  prevents 8 unused bytes per instance. Identity fallback keeps them.
- Layout: closed value records have only their declared fields. A closed
  record with at most 64 bits of fields can flatten in an atomic array on
  the observed JVM. Flattening is a JVM choice; value-type does not promise
  it.
- Cost of the closed shape: identity output needs a transform for the
  constructor check, also where the form has no enabled fields.
- Observed: Valhalla `27-jep401ea3+1-1` with preview and verification
  accepts a value-class constructor that calls `this(…)`. All three
  constructors, `create`, `getBasis`, both record literals and compiled
  record constants work in both representations. Other JVMs are not verified.
  The [non-nil gate](non-nil-gate.md) adds one native and
  one generated field on the same JVM with `-Xverify:all`; it used a
  spike-local transform, and the test suites cover the production transform.
- Risk: records with enabled non-nil fields reject `nil` in `create`,
  record literals and `assoc`, where records usually accept it. Callers that
  build records from partial maps must supply each enabled field.
- Risk: records implement `java.io.Serializable`. The behavior of serializable
  value classes on the EA JVM is not verified. This design does not promise
  serialization.
- Risk: a closed record cannot have metadata or extension keys. A caller
  that uses `__meta` or `__extmap` in a closed type fails differently in the
  two outputs: value output gives `:removed-field-access` when it defines
  the class; identity output gives `IllegalArgumentException` when the code
  passes a non-nil value to the main constructor. The caller obligations
  state the limit.
- Risk: closed-record supports Clojure 1.11. value-type tests only
  Clojure 1.12.6. A caller that uses value output must use a Clojure version
  that value-type supports.
