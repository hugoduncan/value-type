---
title: Test classes that Clojure generates, and their value-class form
status: active
category: testing
tags: [testing, deftype, classloader, DynamicClassLoader, valhalla, value-objects, fixtures]
related: [mementum/memories/clojure-compiler-loader-hook.md, mementum/memories/valhalla-jdk-selection-java-cmd.md, mementum/knowledge/java-lang-classfile.md]
depends-on: []
---

# Test classes that Clojure generates

Source: task `002-value-class-transform`
(`test/value_type/impl/transform_test.clj`: `compile-fixture`,
`round-trip-loader`). Verified on Clojure 1.12.6, JDK 25 and JDK
`27-jep401ea3`.

## Get the class file bytes of a `deftype`

Bind `*compile-files*` to true and `*compile-path*` to a new temp
directory around `eval` of the form. The compiler writes each class
file (also inner `fn` classes, `Name$fn__123`) to that directory.
This uses only public API. `defrecord` works the same way.

- Bind `*ns*` to a namespace that exists (for example the test
  namespace), so that protocols and symbols resolve.
- Use a unique class name for each fixture (`gensym`). See "Static
  cache".
- Keep the `Class` that `eval` returns. Do not look up the class by
  name later.
- Clean up: call `.deleteOnExit` on each file of `(file-seq dir)`.
  `file-seq` is pre-order and the JVM deletes in reverse order, so
  files go before their directory.

## Static cache of `DynamicClassLoader`

`clojure.lang.DynamicClassLoader` keeps one static class cache by
name for all instances. Each `defineClass` puts the class in it. Thus
a second definition of a name replaces the first in the cache:

- each fixture needs a unique class name;
- after a round trip, a lookup by name can give the value class, not
  the `deftype` class.

## Round-trip loader: load changed bytes and keep the links

Problem: define only the changed class in a new loader, and the inner
`fn` classes that Clojure defined still link to the old class in the
`eval` loader. A method that uses an inner `fn` then can throw
`ClassCastException`.

Recipe (child-first, one new loader for each round trip):

- a `proxy` of `DynamicClassLoader` with a new `DynamicClassLoader`
  as parent;
- override `loadClass`:
  - the fixture name: define the changed bytes;
  - a name that starts with `<fixture name>$`: define the class file
    from the `*compile-path*` directory;
  - each other name: load from the parent. Through the static cache,
    the parent finds the protocol interfaces that `eval` defined, so
    the new class implements the same interfaces.
- override both `loadClass` arities (`[name]` and `[name resolve]`)
  with one shared `letfn`. A call from the proxy to the protected
  2-arity of the superclass is reflective.
- define with the public `DynamicClassLoader.defineClass(String,
  byte[], Object)`. A `proxy` cannot call protected `ClassLoader`
  methods (`findLoadedClass`, `defineClass(name, bytes, off, len)`).
  Keep the defined classes in an atom.
- create instances with `clojure.lang.Reflector/invokeConstructor`.

## Compare value objects with `deftype` instances

- A `deftype` instance and a value object are never `=` and never
  `identical?`. Compare method results, not instances. When a method
  returns the receiver, check each result is `identical?` to its own
  receiver.
- `identical?` (`acmp`) on two value objects compares an `Object`
  field by reference. Give both objects the same `Object` instance.
- A value object with no `equals` override compares fields, so `=`
  works on two value objects.
- Do not use `NaN` in values that tests compare with `=` (Clojure `=`
  on `NaN` is false).

## Valhalla-only API in tests

`Class::isValue` exists only on a Valhalla JDK. A hinted
`(.isValue ^Class c)` is reflective with a warning on other JDKs.
Use `(Reflector/invokeInstanceMethod c "isValue" (object-array 0))`.
Then the test namespace loads with no warnings on each JDK, and
`(set! *warn-on-reflection* true)` stays useful.
