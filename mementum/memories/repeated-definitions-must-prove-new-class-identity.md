❌ Successful construction after repeated macroexpansion transfer does not
prove that each receiving operation defined a class. An earlier class or
factory can make a skipped definition pass.

Give each independent policy, receiver-mode and expansion-form case its own
cell: a fresh namespace (`cell.<i>`, so a unique class name), its own
`Compiler/LOADER` frame, and a new-Class check (`cell-registry/new-class!`:
the Class was not seen before in this JVM). Cells of one policy can share a
child JVM; a policy keeps its own JVM. Assert that the factory constructs
exactly the defined Class (`cell-registry/defined` → `:new-class?
:factory?`), then check field behavior and rejection (`cell-registry/absent?`).
For function-contained definitions, invoke the function; analysis alone does
not prove execution.

Compare returned Classes, not namespace imports. Function-contained redefinition
can leave an import pointing to an earlier Class while returning a new one.
This refines the existing rule to retain the Class returned by `eval` in
[generated-class tests](../knowledge/testing-clojure-generated-classes.md).

Source: task `016-nil-restricted-fields` (`ea3224b`); fresh-JVM rule replaced
by the cell model in task `018-fast-compatibility-matrix`.
