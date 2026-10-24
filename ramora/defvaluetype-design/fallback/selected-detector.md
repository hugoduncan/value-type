## Selected detector

The baseline-safe `impl.capability/detect` checks ClassFile by name and
Class.isValue by reflection. Only ClassNotFoundException for ClassFile and
NoSuchMethodException for isValue select API absence. After these checks,
use ClassFile.latestMajorVersion and preview minor 65535 for the known
strict-field probe. Define it with a unique name in a fresh loader, construct
it, check the value predicate and read back its field. Only
UnsupportedClassVersionError from that definition permits format fallback.
Other failures, including invariant failures, remain errors. Do not parse
exception messages or catch user-definition errors to select fallback.

This matches the transformer version rule. It verifies the selected EA
build, not future class formats. Missing later API members remain visible
errors rather than an untested absence classification.
