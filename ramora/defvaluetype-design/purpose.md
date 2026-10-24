# Purpose

## Mission

JEP 401 adds value classes to the JVM. A value object has no identity.
The JVM can flatten and scalarize value objects. This can decrease
allocation and memory use.

Clojure cannot declare a value class. `deftype` always makes an
identity class. `defvaluetype` closes this gap. A Clojure programmer
can then use JEP 401 value objects without Java source.

## Desired outcomes

- User outcome: a programmer changes `deftype` to `defvaluetype` and
  gets a value class when supported, or an ordinary `deftype` under
  the application fallback policy. Libraries that permit fallback must
  work with both identity and value semantics.
- Technical outcome: the class has the same bytecode quality as a
  value class that `javac` makes. No runtime overhead is added.
- Learning outcome: the project shows how Clojure can use Valhalla
  features before Clojure supports them.

## Capabilities

The macro must:

1. Accept the `deftype` syntax: name, fields, options, specs.
2. Make a final value class with final fields when supported.
3. Implement protocols, interfaces and `Object` methods as `deftype`
   does.
4. Make the positional factory `->Name`.
5. Support REPL evaluation and redefinition.
6. Support AOT compilation. The `.class` file must keep the selected
   representation. Loading it does not select a new representation.
7. Reject forms that a value class cannot express. Give a clear error.
8. When value classes are unavailable, support silent fallback, one
   warning followed by fallback, or an `Error` on every attempt.
9. Let applications control fallback without changes to dependencies.

## Scope

Included:

- Concrete value classes that extend `Object`.
- All field type hints that `deftype` accepts.
- The `:load-ns` option.
- Controlled fallback to ordinary `deftype` semantics.

Excluded:

- Mutable fields (`:volatile-mutable`, `:unsynchronized-mutable`).
- A record form (`defrecord` semantics). Records use mutable hash
  cache fields.
- Abstract value classes.
- Null-restricted field types. This is a separate JEP.
- Anonymous value objects (a `reify` form).
- Emulation of value semantics on identity classes.
- Conversion of AOT classes between representations at load time.
- ClojureScript and other hosts.

Value-class environment: a supported Valhalla JDK that implements
JEP 401, run with `--enable-preview`, with Clojure 1.12.
Fallback adds no JDK minimum beyond the supported Clojure runtime.
Verified JDK versions must be stated separately from this design target.

## Behaviour

- Input: a `defvaluetype` form at macroexpansion time.
- Output: a defined class selected by capability and policy, an import,
  a factory function, and,
  when AOT compiling, a `.class` file.
- State: cached capability, application policy and a warning-once flag
  per loaded library runtime. A capture loader exists for one top-level
  form only, on the value-class path.
- Rules: the value-class rules in [Context](context.md).
- Failure: unavailable support under strict policy throws an `Error`
  on every attempt. Other failures with an error key of `value-type` give an
  `ex-info` with `:value-type/error` and the cause. A `deftype` syntax
  error gives the `deftype` error.

Fallback uses ordinary `deftype` semantics, not the rules below.
Runtime semantics of a value object follow the JVM. They differ from
`deftype` objects:

- `identical?` (the Java `==` operator, `acmp`) compares field values
  (substitutability). `clojure.core/==` is numeric and does not apply.
- The default `equals` and `hashCode` use field values.
- A `double` field compares by bit pattern in `identical?`. So `NaN`
  equals `NaN`, and `0.0` does not equal `-0.0`.
- A field that holds an identity object compares by reference. Two
  equal vectors that are not the same object make the value objects
  not equal.
- A field that holds a value object (for example a boxed `Long` under
  `--enable-preview`, or a `defvaluetype` instance) compares by its
  field values.
- `locking`, weak references and identity-sensitive operations throw
  `IdentityException`.

## Success criteria

- On a supported value-class JVM, every `deftype` test form without
  mutable fields compiles as a value class.
- `(.isValue c)` (or the equivalent flag check) is true for each
  class selected for the value-class path.
- AOT output loads in a new JVM and is a value class.
- Method call performance is equal to a `deftype` with the same body.
- Fallback loads without preview or `java.lang.classfile`. It follows
  the selected policy, including concurrent attempts and strict retries.
- Mutable fields fail in both modes. Implementation defects never
  trigger fallback. See [fallback verification](fallback.md#verification).

## Priority structure

1. Correctness: obey application policy; never substitute an identity
   class on the value-class path or under strict policy.
2. `deftype` compatibility.
3. Minimal implementation and minimal coupling to compiler internals.
4. Performance (the JVM gives this when 1 to 3 are correct).

## Lifecycle purpose

A library. It is experimental while JEP 401 is a preview feature.

## Evolution expectations

- JEP 401 class file details can change in each early-access build.
- Null-restricted types and abstract value classes can come later.
- When Clojure supports value classes, this library can retire.
