🔁 Child-JVM test programs must return data. Do not `assert` in the child.

An `assert` in a child program loses the actual value. A surrounding
`(catch Throwable t (.getName (class t)))` reduces it to
`"java.lang.AssertionError"`, which does not identify the check.

Pattern (task 017 test reviews 4–12, about 30 programs changed):

- Put the child logic in a baseline-safe `test/compatibility/**/*_checks.clj`
  function, not a string program or a test namespace. Build child forms
  from data, not by string concatenation or `postwalk-replace`.
- Return the inspected facts as EDN: source absence, `value?`, field type,
  annotations, stored values and outcomes per construction entry point.
- In the parent, compare the complete result with one expected map.
- Report a failure as root class and message. Comparing the class alone
  lets any plain `java.lang.Error` pass a policy-rejection check.

`with-redefs` of a Var compiles only after its namespace has loaded. Require
that namespace in a preceding top-level form of the child program.

Examples: `consumer_aot_checks.clj`, `function_aot_checks.clj`,
`field_policy_checks.clj`.
