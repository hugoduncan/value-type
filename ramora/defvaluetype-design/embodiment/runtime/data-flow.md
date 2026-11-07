# Runtime data flow

Part of the [runtime embodiment](../runtime.md).

Representation selection for definitions without enabled non-nil fields:

```text
validate fields → determine capability and application policy
  available   → existing value-class flow below
  unavailable → silent: deftype
                warn: warn once, then deftype
                error: throw Error on every attempt
```

Fallback expansions also enforce policy where the form is evaluated (D13).
For AOT, top-level forms enforce policy during namespace initialization.
Forms inside functions permit namespace loading and enforce policy on
invocation; repeated strict calls throw plain Error. Direct class access
that bypasses the guarded form is not protected, and guards cannot prevent
all prior class loading. Failed initializer retries can throw
NoClassDefFoundError without guard re-entry.
Fallback expansions do not install a capture loader or call `check-value-class`.
The existing value-class flow remains:

```text
macroexpand defvaluetype
  core: validate fields                 → ex-info :mutable-field
  core: installed := (loader/install! "ns.Name" [] :value)
                                        → LOADER := capture loader
  core: return (let [] (deftype ...)
                       (check-value-class ns.Name <installed>)
                       ns.Name)
        ;; fully qualified: the import has not run at analysis time
analyze (deftype* ...)
  compiler: emit bytes
  compiler: write .class (AOT only)
  compiler: LOADER.defineClass("ns.Name", bytes)
    loader: bytes' := transform/->value-class bytes storage delegate
            ;; value-backend/prepare: storage from [] (empty),
            ;; delegate resolves stack-map types
    loader: write bytes' (AOT only)
    loader: delegate.defineClass("ns.Name", bytes')
    loader: link (getDeclaredMethods)
    loader: define or link LinkageError → ex-info :jvm-rejected
            ;; ClassFormatError: define; VerifyError: link
runtime
  check-value-class                     → ex-info :not-installed
                                          or :not-value-class
```

## Enabled non-nil fields

`defvaluetype` validates mutable fields, constraint metadata and concrete tags
before selecting representation and ordered enforcement modes. Value output uses
native enforcement where eligible and available, otherwise generated checks.
Identity output uses generated checks and remains subject to fallback policy.
Selection does not install a loader.

Both public macroexpansion forms retain a receiving compiler boundary:
`core/value-field-deftype` or `core/identity-field-deftype`. Receiving analysis
resolves exact Classes and installs the capture loader with fixed specifications.
Unsupported receivers reject the selected mode rather than reselect enforcement.
Emitted forms retain evaluation-time representation guards.

Before target definition, the loader checks each selected Class through its delegate.
A different Class gives `:invalid-field` with `:reason :field-class-mismatch`.
Bundled ASM transforms identity output; `java.lang.classfile` transforms value
output. Both preserve concrete storage, constructor checks and owner/companion
field references. Captured companions keep their class kind. Both paths write
transformed bytes during AOT. See the
[field embodiment](../../../non-nil-field-design/embodiment.md) for evidence
and accepted evidence limits.
