## Selected detector

The baseline-safe `impl.capability/detect` first reads the value-class switch
(D18), so `off` and an invalid value give the same result on every JVM. Then it
checks ClassFile by name and Class.isValue by reflection. Only
ClassNotFoundException for ClassFile and NoSuchMethodException for isValue
select API absence. After these checks, the probe builder (D16) makes the
known strict-field probe with the D15 format row. Define it with a unique name
in a fresh loader through the compiler boundary (D19), construct it, check the
value predicate, read back its field and run the semantic self-check (D17).
Only these results permit format fallback: UnsupportedClassVersionError from
the definition, a probe class that is not a value class, and a failed
self-check (D12). Other failures remain errors. Do not parse exception
messages or catch user-definition errors to select fallback.

This uses the same format row as the transformer. It verifies the claimed EA
build. A future build with no row uses the default row; the probe then shows
whether the default is correct. Missing later API members remain visible
errors rather than an untested absence classification.
