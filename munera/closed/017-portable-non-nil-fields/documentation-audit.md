# Documentation acceptance audit

This audit closes only the README/usage and public API documentation checklist
items. The design contract is unchanged. Acceptance 9 also requires regression
verification and remains open.

## Contract coverage

- `README.md`, “Required, typed fields”, introduces portable declarations, concrete
  tags, generated/native enforcement and removed-key rejection. It links the full
  usage contract rather than repeating all exclusions.
- `doc/usage.md`, “Portable non-nil fields”, specifies both metadata forms, disabled
  constraints, eligible and rejected tags, final concrete storage, Object constructor
  parameters, construction exceptions and excluded mutation paths. It separates
  representation policy from enforcement and distinguishes fixed AOT from source use.
- `src/value_type/core.clj`, the `defvaluetype` docstring, specifies metadata,
  declaration errors, constructor checks, enforcement choice, receiving analysis and
  fixed AOT representation. `doc/API.md` contains the generated public reference.
- `doc/development/runtime.md` records explicit commands and separates real JVM
  evidence from controlled capability cases. Packaged-source evidence is limited
  to silent policy. Remaining matrix work is not presented as complete acceptance.
- Field and macro Ramora embodiment describe the implemented backends and link
  evidence limits. Historical experiments remain separate from production claims.

## Verification

`bb api-docs --check` exits 0: the generated reference matches its source.
`bb vale` exits 0 with an empty alert map. `git diff --check` passes.
These are documentation checks, not new runtime or controlled-capability evidence.

Controlled public native absence, saved-bytecode coverage and the remaining
acceptance audit stay open. No new JVM support is established by this audit.
