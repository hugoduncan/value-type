# value-type API reference

This file is generated from source docstrings with Quickdoc. Do not edit `doc/API.md` by hand.

See the [README](../README.md) for installation, examples and tested JVMs.

Regenerate from the repository root with `bb api-docs`. Check without writing with `bb api-docs --check`.

# Table of contents
-  [`value-type.core`](#value-type.core)  - Define JEP 401 value classes with [<code>defvaluetype</code>](#value-type.core/defvaluetype), a <code>deftype</code> compatible macro.
    -  [`defvaluetype`](#value-type.core/defvaluetype) - As <code>deftype</code> (see its docstring for syntax, options, specs, the <code>-&gt;name</code> factory and the import).

-----
# <a name="value-type.core">value-type.core</a>


Define JEP 401 value classes with [`defvaluetype`](#value-type.core/defvaluetype), a `deftype`
  compatible macro.




## <a name="value-type.core/defvaluetype">`defvaluetype`</a>
``` clojure
(defvaluetype name [& fields] & opts+specs)
```
Macro.

As `deftype` (see its docstring for syntax, options, specs, the
  `->name` factory and the import). Define a final JEP 401 value class
  when supported, or an ordinary identity class under fallback policy.

  Uses value classes on a supported JEP 401 JDK with `--enable-preview`.
  Otherwise uses ordinary identity `deftype`. Set JVM property
  `value-type.fallback` before loading: silent (default), warn (once per
  library runtime), or error (reject with Java Error). Compiler operations
  wrap that Error in CompilerException. Configuration is read and validated
  once; invalid values fail and later changes are unsupported. Requiring
  alone does not warn. Concurrent types and namespaces share warning state.

  Selecting fallback during macroexpansion applies policy even if the
  expansion is discarded. Returned expansions enforce receiving policy at
  compilation and evaluation. Strict retries reject after a caught failure.
  Fallback retains ordinary identity, equality and hashing semantics.
  Invalid forms and implementation defects are not retried as fallback.

  AOT fixes representation. Fallback enforces receiving policy where the
  form is evaluated, even on a capable JVM. Top-level forms enforce it during
  namespace initialization. Forms inside functions permit namespace loading
  and enforce policy on invocation; repeated strict calls throw plain Error.
  Failed initialization retries can throw NoClassDefFoundError (also an Error).
  Guards do not cover direct class access that bypasses evaluation of the
  guarded form, or guarantee that no class was loaded before rejection.
  Value artifacts do not convert.
  Fallback adds no minimum JDK beyond the supported Clojure runtime; see
  README for tested JVM versions and verification commands.

  Restriction: no mutable fields (`:volatile-mutable` or
  `:unsynchronized-mutable`).

  In value mode, differences from `deftype`: instances have no identity.
  - `identical?` (the Java `==` operator, `acmp`) compares field values.
  - The default `equals` and `hashCode` use field values.
  - A `double` field compares by bit pattern in `identical?` (`NaN`
    equals `NaN`; `0.0` does not equal `-0.0`).
  - A field that holds an identity object compares by reference (two
    equal vectors that are not the same object give instances that
    are not equal).
  - A field that holds a value object (for example a boxed `Long`
    under `--enable-preview`, or a [`defvaluetype`](#value-type.core/defvaluetype) instance) compares
    by its field values.
  - `locking`, `WeakReference` and other identity operations throw
    `IdentityException`.
  - AOT output is tied to one JDK feature release.

  Errors (`ex-info` with `:value-type/error` and `:class-name`):
  - `:mutable-field` (macroexpansion; data `:fields`): a mutable field.
  - `:not-installed` (run time): macroexpanded with no compilation
    loader binding, then evaluated.
  - `:not-value-class` (run time): the class is not a value class.
  - `:jvm-rejected`, `:unexpected-constructor`: from the loader.
<p><sub><a href="https://github.com/hugoduncan/value-type/blob/master/src/value_type/core.clj#L84-L153">Source</a></sub></p>
