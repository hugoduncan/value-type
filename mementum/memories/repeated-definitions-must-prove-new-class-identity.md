❌ Successful construction after repeated macroexpansion transfer does not
prove that each receiving operation defined a class. An earlier class or
factory can make a skipped definition pass.

Give each independent policy, receiver-mode and expansion-form case a fresh
JVM. Within that case, repeat the operation and assert that each returned
Class differs by identity from the previous Class. Assert that the factory
constructs exactly the returned Class, then check field behavior and rejection.
For function-contained definitions, invoke the function; analysis alone does
not prove execution.

Compare returned Classes, not namespace imports. Function-contained redefinition
can leave an import pointing to an earlier Class while returning a new one.
This refines the existing rule to retain the Class returned by `eval` in
[generated-class tests](../knowledge/testing-clojure-generated-classes.md).

Source: task `016-nil-restricted-fields`, transfer isolation review and fix
`ea3224b`. Verified on Clojure 1.12.6 and Valhalla EA3.
