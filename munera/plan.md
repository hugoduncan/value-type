# Plan

Source: residual (stressor) analysis of commit `5073893f`. Design: defvaluetype
D15–D24 and the changed decisions; the
[embodiment gap table](../ramora/defvaluetype-design/embodiment.md#decided-changes-not-yet-implemented)
lists each gap.

1. `024-fallback-policy-first-read`: policy at first read (D10, D13) and
   `:fallback-policy` in the report (D14).
2. `025-value-class-switch`: `value-type.value-classes` (D18) without the AOT
   part. Uses the probe namespace and reasons of closed task 023.
3. `026-compiler-boundary-and-receiving-expansion`: compiler boundary (D19),
   receiving expansion (D20), AOT initialization check (D17, D18). After 025.
4. `027-jvm-facts-test-tiers-and-releases`: facts file (D21), test tiers with
   the forced-JIT row (D22), tested-release installation (D24). Independent.
5. `028-disassembly-snapshots`: D23. Last: 026 changes emitted output.
