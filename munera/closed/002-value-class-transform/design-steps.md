# Design steps

- [x] Architecture: the decision "Use the JDK constants, not literals"
  conflicts with the Testability target in
  `ramora/defvaluetype-design/qualities.md` ("The transform is
  testable without a Valhalla JVM. Unit tests run on bytes."). With
  `ClassFile/ACC_IDENTITY` and `ClassFile/ACC_STRICT_INIT`, the
  namespace and all of its unit tests fail to load on a non-Valhalla
  JDK. The design cites D10 as the reason, but D10 is about a runtime
  fallback of `defvaluetype`, not about where the tests can run.
  Make the decision state this trade-off: name the Testability target
  that it degrades, and give the reason without D10 (for example: the
  supported environment is a Valhalla JDK, and the JDK constants follow
  format changes in a new build). Alternatively, keep the target and
  use literals in `transform`. Do not change the scope.
- [x] Ambiguity: scope item 3 does not say which operands the shape
  check compares. Write the exact rule. For field `i` in declaration
  order: the load opcode matches the field type kind; the slot is the
  slot of parameter `i` (`long` and `double` use two slots); `putfield`
  has owner = this class and the name and descriptor of field `i`. The
  super call is `invokespecial java/lang/Object.<init>()V`. Also say
  what happens with code elements that are not labels, line numbers,
  local variable entries or instructions (for example an exception
  table entry): mismatch or ignored. Say which of the four message
  parts (count, parameters, super call, field writes) reports each
  other mismatch, for example a missing `return` or extra instructions.
- [x] Ambiguity: Verification 2 says "defined in a new class loader"
  but does not say which loader, or how the inner `fn` class of the
  fixture resolves the value class. The inner `fn` class that Clojure
  defined links to the identity class in the `eval` loader. So
  "methods give the same results" can fail with `ClassCastException`
  (the reason D3 rejects "define the class again"). Specify the
  loader. For example: a child-first loader that defines the
  transformed bytes and loads the other class files of the fixture
  from its `*compile-path*` directory. Then all classes of the fixture
  link in that loader.
- [x] Ambiguity: acceptance criterion 2 says "the expected
  `:var-count`" but gives no value or rule. State the rule, for
  example: equal to the count of `deftest` vars in
  `value-type.impl.transform-test`, and greater than 0.
- [x] Inconsistency: the Intent ("The JVM must accept the output") and
  the first Invariant ("For accepted input, the output is a value class
  that the running JVM defines with no error") contradict "Out of
  scope": the input check does not look at field flags, so it accepts
  a `deftype` with a mutable (non-final) field, and the JVM rejects
  that output. Qualify the Intent and the Invariant, for example "for
  accepted input whose instance fields are final".
- [x] Inconsistency: "Out of scope" says "The loader task wraps that
  error (D9)". The Intent and `ramora/defvaluetype-design/embodiment.md`
  assign D9 to the macro task (`value-type.core`), and the loader to
  D3, D4, D7. Make the task names agree.
- [x] Inconsistency: scope item 2 lists these as pass-through parts,
  but Verification 1 does not check them: this class, the constructor
  name, descriptor and flags, and the instance field names,
  descriptors and attributes (only the field flags are checked).
  `embodiment.md` makes the transform unit tests the enforcement point
  of "the transform changes only version, class flags, field flags,
  constructor". Make Verification 1 cover each pass-through part in
  scope item 2.
- [x] Architecture: the "Error contract" message names only the part
  that does not match (count, parameters, super call, field writes).
  The Error clarity target in `ramora/defvaluetype-design/qualities.md`
  is "The error names the cause and the fix". `:unexpected-constructor`
  is in the public error set of `embodiment.md`, and `value-type.core`
  does not wrap it, so `defvaluetype` users see this message. The
  cause in `risks-and-evolution.md` is a change in Clojure `deftype`
  output; the supported environment is Clojure 1.12. Make the message
  contract also give the cause (the constructor is not the `deftype`
  shape that this library supports) and the fix (use the supported
  Clojure version). Do not change the scope or the check.
- [x] Ambiguity: Verification 2 does not say which fixtures of
  Verification 1 have a round trip, or what "the methods give the same
  results as on the `deftype` instance" means when a result is (or
  contains) an instance of the fixture class. The inner `fn` fixture
  "uses `this`", so its result is a `deftype` instance on one side and
  a value object on the other. They are never `=` or `identical?`.
  State the fixtures (for example: each fixture of Verification 1) and
  the comparison rule (for example: compare results with `=` when they
  are not fixture instances; when a method returns the receiver, check
  that the result is `identical?` to the receiver).
- [x] Ambiguity: Verification 3 says "a built input with one
  constructor of a different shape" but does not say which shape, so
  it does not say which message part ("parameters", "super call",
  "field writes") the test covers. It also does not say if the tests
  check the message part. State the built mismatch fixtures and the
  expected message part of each, and say whether the test asserts
  the message part or only `:value-type/error` and `:class-name`.
- [x] Inconsistency: the decision "Flag values are literals" says "the
  flag values are only in `transform`" (the Changeability fitness
  condition in `ramora/defvaluetype-design/qualities.md`). But
  Verification 1 makes the test namespace check the class flags (input
  without `ACC_IDENTITY`) and the field flags (input plus
  `ACC_STRICT_INIT`). The design does not say where the tests get
  0x20 and 0x800. Literals in `value-type.impl.transform-test` put the
  values in a second namespace. The Risk text ("Then the round-trip
  tests fail") also implies that the structure tests do not find a
  flag change. Make these agree: state the source of the flag values
  in the tests (for example: the private vars of `transform`), or
  state that test literals are an exception to the condition.
- [x] Ambiguity: Verification 1 says "with `long`, `double`, `int`
  and `Object` fields". It does not say if this is one fixture with all
  four fields or four fixtures with one field each. With one field per
  fixture, no accepted input has a field after a `long` or `double`
  field. Then no passing test covers the rule `s(i+1)` = `s(i)` + 2 of
  scope item 3, and no round trip covers the two-slot loads in the new
  constructor. State the fixture: for example, one fixture with the
  fields in the order `long`, `int`, `double`, `Object`, so that a
  field follows each two-slot field.
- [x] Plan ambiguity: plan.md decision 6 ("Pass-through comparison")
  says attributes have no value equality, but it gives a rule only for
  names, descriptors, flags and the code bytes of other methods. It
  does not say how the structure tests compare the class attributes
  and the attributes of instance and static fields (steps.md slice 4
  item 2 says "equal the input"). State the rule in plan.md. For
  example: compare the list of attribute names, and compare each
  attribute by kind (`SourceFileAttribute` by `.sourceFile` string,
  `SourceDebugExtensionAttribute` by `.contents` bytes); an attribute
  of another kind fails the test, so a new kind is not ignored.
- [x] Plan ambiguity: plan.md risk 1 says "fix the check (not the
  fixtures) if the shape differs within the documented contract". The
  design gives the check rule exactly (scope item 3), so it is not
  clear what the implementer may change when Clojure output for a
  design fixture fails the check. Also, only the five-field fixture
  was checked for the design, not "the five-fixture set". State the
  action. For example: stop, record the actual constructor elements in
  implementation.md, and raise a design question; do not change the
  check rule or the fixture list in the implementation.
- [x] Plan inconsistency: design.md scope item 2 says the output
  changes only the constructor code of the constructor. All other
  parts pass through. plan.md decision 5 ("Keep the constructor name,
  descriptor and flags") and steps.md slice 1 follow the spike:
  `.withMethod` + `.withCode`. This makes a new method and drops each
  other constructor method element (for example `Exceptions`,
  `Signature`, annotations). The input check does not reject these
  elements. Make the plan agree with the design. For example: use
  `transformMethod` with a `MethodTransform` that replaces only the
  `CodeModel` and passes the other method elements through with
  `.with`.
- [x] Plan inconsistency: plan.md decision 7 and steps.md slice 7 set
  `JAVA_CMD` to the JDK home (`~/.local/jdks/jdk-27.jdk/Contents/Home`).
  design.md ("`JAVA_CMD` set to the Valhalla `java`"), README.md and
  the memory `valhalla-jdk-selection-java-cmd` say that `JAVA_CMD` is
  the `java` executable. Change the value to the absolute path
  `<valhalla-jdk-home>/bin/java`
  (`/Users/duncan/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`).
