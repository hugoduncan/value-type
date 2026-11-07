# Plan

Source: residual (stressor) analysis of commit `5073893f`. Design: defvaluetype
D15–D24 and the changed decisions; the
[embodiment gap table](../ramora/defvaluetype-design/embodiment.md#decided-changes-not-yet-implemented)
lists each gap.

1. `026-compiler-boundary-and-receiving-expansion`: compiler boundary (D19),
   receiving expansion (D20), AOT initialization check (D17, D18). Uses the
   switch of closed task 025.
2. `027-jvm-facts-test-tiers-and-releases`: facts file (D21), test tiers with
   the forced-JIT row (D22), tested-release installation (D24). Independent.
3. `028-disassembly-snapshots`: D23. Last: 026 changes emitted output.
