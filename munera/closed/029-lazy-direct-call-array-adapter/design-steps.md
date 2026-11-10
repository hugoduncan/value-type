# Design follow-up

- [x] Clarify the JVM configurations in acceptance criterion 2. State that
  Java 8 require and require-only application AOT/load checks use baseline
  launch options, and that the independent preview/export on/off combinations
  apply to EA3. The current wording can apply those combinations to Java 8,
  which rejects these flags before library loading can be tested.
