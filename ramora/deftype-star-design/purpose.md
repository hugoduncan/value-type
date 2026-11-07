# deftype* purpose and priorities

Let a library that emits `deftype*` directly get value-class output. The first
user is closed-record: its `defclosedrecord` emits a record-shaped `deftype*`
and will select value output with a `:value-type true` option.

Priorities: keep the Clojure record contract (record literals, code constants,
the static `create` method); obey the same application fallback policy as
`defvaluetype`; add no per-instance cost for record support; keep the public
surface small.

A caller that does not use metadata or extension keys can select the closed
record shape
([D9](decisions/closed-record-shape.md#d9-select-the-closed-record-shape-with-an-option-interface)).
Value output then has only the declared fields, so the JVM can flatten small
records. closed-record uses it for `:value-type true`.

Success means a record-shaped `deftype*` gives a value class on a supported
JVM and an identity class under fallback policy. In both representations, the
compiler-generated constructors and `create` work with no change.
