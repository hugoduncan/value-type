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
  gets a value class. No other change is necessary for programs that
  do not use identity.
- Technical outcome: the class has the same bytecode quality as a
  value class that `javac` makes. No runtime overhead is added.
- Learning outcome: the project shows how Clojure can use Valhalla
  features before Clojure supports them.

## Capabilities

The macro must:

1. Accept the `deftype` syntax: name, fields, options, specs.
2. Make a final value class with final fields.
3. Implement protocols, interfaces and `Object` methods as `deftype`
   does.
4. Make the positional factory `->Name`.
5. Support REPL evaluation and redefinition.
6. Support AOT compilation. The `.class` file on disk must be the
   value class.
7. Reject forms that a value class cannot express. Give a clear error.
8. Fail loudly when the JVM cannot define a value class. Never make an
   identity class silently.

## Scope

Included:

- Concrete value classes that extend `Object`.
- All field type hints that `deftype` accepts.
- The `:load-ns` option.

Excluded:

- Mutable fields (`:volatile-mutable`, `:unsynchronized-mutable`).
- A record form (`defrecord` semantics). Records use mutable hash
  cache fields.
- Abstract value classes.
- Null-restricted field types. This is a separate JEP.
- Anonymous value objects (a `reify` form).
- A fallback to an identity class on JVMs without value classes.
- ClojureScript and other hosts.

Supported environment: a Valhalla JDK that implements JEP 401, run
with `--enable-preview`, with Clojure 1.12.

## Behaviour

- Input: a `defvaluetype` form at macroexpansion time.
- Output: a defined value class, an import, a factory function, and,
  when AOT compiling, a `.class` file.
- State: none that persists. A capture loader exists for one top-level
  form only.
- Rules: the value-class rules in [Context](context.md).
- Failure: an `ex-info` with a `:value-type/error` key and the cause.

Runtime semantics of a value object follow the JVM. They differ from
`deftype` objects:

- `==` and `identical?` compare field values (substitutability).
- The default `equals` and `hashCode` use field values.
- Fields that hold references compare by reference. Two equal vectors
  that are not the same object make the value objects not equal.
- `locking`, weak references and identity-sensitive operations throw
  `IdentityException`.

## Success criteria

- Every `deftype` test form without mutable fields compiles as a value
  class.
- `(.isValue c)` (or the equivalent flag check) is true for each
  defined class.
- AOT output loads in a new JVM and is a value class.
- Method call performance is equal to a `deftype` with the same body.

## Priority structure

1. Correctness: never make an identity class silently.
2. `deftype` compatibility.
3. Minimal implementation and minimal coupling to compiler internals.
4. Performance (the JVM gives this when 1 to 3 are correct).

## Lifecycle purpose

A library. It is experimental while JEP 401 is a preview feature.

## Evolution expectations

- JEP 401 class file details can change in each early-access build.
- Null-restricted types and abstract value classes can come later.
- When Clojure supports value classes, this library can retire.
