# Value-type behavior and limitations

See the [README](../README.md) for installation and a quick start, and the
[API reference](API.md) for the macro contract.

## Fallback policy

Fallback adds no minimum JDK beyond the supported Clojure runtime. On a
JVM without supported value-class capability, `defvaluetype` uses ordinary
identity `deftype`. It does not emulate value equality or identity. Libraries
that allow fallback must work with both representations.

Set the JVM property before dependencies load, not in `-main`:

```sh
clojure -J-Dvalue-type.fallback=error -M -m your.application
```

| `value-type.fallback` | Behavior when fallback is needed |
|---|---|
| `silent` (default) | Use `deftype` without a diagnostic. |
| `warn` | Warn on standard error once per loaded library runtime. |
| `error` | Reject every attempt with `java.lang.Error`. |

Configuration is read and validated once. Invalid values fail at load time;
later property changes are unsupported. There are no per-type overrides.
All policies use value classes when supported. Requiring the library alone
does not warn. Concurrent attempts across types and namespaces share one
warning flag; independent library runtimes have separate flags.

Selecting fallback during `macroexpand` or `macroexpand-1` applies policy,
even if the expansion is discarded. Returned expansions retain identity
representation and enforce the receiving runtime's policy when compiled
and evaluated. Catching a strict failure does not permit a later attempt.
Source compiler operations wrap the Error in `Compiler$CompilerException`;
inspect its cause. A direct runtime policy rejection throws plain Error.
Invalid forms and loader or transform defects remain failures, not fallback.
Both representations reject mutable fields.

## AOT limits

AOT compilation fixes representation. A fallback artifact stays an identity
class on a capable JVM. The guard applies the receiving application's policy
where the fallback form is evaluated: warn once or reject under `error`.
A top-level form enforces policy during namespace initialization. A form
inside a function permits namespace loading, even under `error`, and enforces
policy when that function runs. Repeated strict calls each throw plain Error.
After strict initialization failure, a retry can throw `NoClassDefFoundError`
rather than run the guard again; this is also a Java Error. Guards cannot
prevent all prior JVM class loading and do not cover direct class access
that bypasses evaluation of the guarded form.

Value-class AOT output uses the compiling JDK's feature version and preview
minor version. It requires a compatible preview-enabled JVM; it does not
convert to identity classes on another JVM.

## Differences from `deftype`

In value mode, objects have no identity. These differences come from the JVM.
They do not apply to ordinary identity fallback:

- `identical?` (the Java `==` operator, `acmp`) compares field values.
- The default `equals` and `hashCode` use field values.
- A `double` field compares by bit pattern in `identical?`. So `NaN`
  equals `NaN`, and `0.0` does not equal `-0.0`.
- A field that holds an identity object compares by reference. Two
  equal vectors that are not the same object make the value objects
  not equal.
- A field that holds a value object (for example a boxed `Long` under
  `--enable-preview`, or a `defvaluetype` instance) compares by its
  field values.
- `locking`, `WeakReference` and other identity operations throw
  `IdentityException`.

Other differences:

- No mutable fields. A field with truthy `:volatile-mutable` or
  `:unsynchronized-mutable` metadata gives an error.
- Field and value-path errors are `ex-info` with the key `:value-type/error` and
  `:class-name`:
  - `:mutable-field`: a field is mutable (at macroexpansion).
  - `:not-installed`: the form was macroexpanded outside compilation.
    Evaluate the `defvaluetype` form directly.
  - `:not-value-class`: the class is an identity class. The causes
    and fixes:
    - The form was not compiled directly (for example
      `(eval (macroexpand form))`). Evaluate the `defvaluetype` form
      directly.
    - A `defvaluetype` is inside a method body of another
      `defvaluetype`. Move the inner form out of the method body to
      the top level.
  - `:jvm-rejected`: the JVM rejected a user class on the selected
    value path. This is not retried as fallback.
  - `:unexpected-constructor`: the Clojure compiler output has an
    unknown shape.
  - A `deftype` syntax error gives the error of `deftype`.
- How you get the error: `:mutable-field` is the cause of a
  `CompilerException` (macroexpansion error). `:jvm-rejected` and
  `:unexpected-constructor` are the cause of a `CompilerException`
  (the class is defined in analysis). `:not-installed` and
  `:not-value-class` come directly from `eval`, and are the cause of a
  `CompilerException` from `load`, `require` and `load-string`. Use
  `ex-cause` to get the `ex-info` from the `CompilerException`. A
  `(catch clojure.lang.ExceptionInfo ...)` does not catch a
  `CompilerException`.
- Value-class AOT output uses the class file version of the compiling JDK
  with the preview minor version. It loads only on the same JDK feature
  release with preview enabled. Ordinary fallback output has neither of
  these value-class requirements.
