# JAR build

## Intent

The library needs a distributable JAR that a Clojure consumer can use
without a source checkout. Success means that the JAR contains the library
sources, dependency metadata and license, and that a consumer can use
`defvaluetype` from the JAR on a supported Valhalla JDK.

## Context

The project has no build command or license file. Its runtime dependency
is Clojure 1.12.6. Development aliases add test, benchmark and REPL tools;
these are not library dependencies.

The reference is
`~/projects/hugoduncan/closed-record/add-jar/build.clj`. It uses
`clojure.tools.build` to produce a source-only library JAR and derive its
version from the Git commit count. Follow this approach, with metadata
for value-type.

The user confirmed the source-only scope, coordinates, version scheme and
EPL-2.0 license. No AOT compilation, bundled dependencies, installation
or publication is required.

## Scope and contract

- Provide `clojure -T:build jar` and `clojure -T:build clean` from the
  repository root.
- Use the Maven coordinates `org.hugoduncan/value-type`.
- Compute the version as `0.1.N`, where N is the output of
  `git rev-list --count HEAD`. Require Git and usable repository history
  for the JAR build. Do not use a fallback version. A shallow checkout
  reports only its available history; document that limitation.
- Produce `target/value-type-0.1.N.jar`. Return the JAR path and version
  from the build command, as in the reference.
- Package all library sources under `src/`, Maven metadata and the
  EPL-2.0 license. Do not package compiled classes, dependencies, tests,
  benchmarks, build code or development records.
- Generate dependency metadata from the project runtime dependencies,
  without user-level dependency configuration or development aliases.
  Include the project description, project URL, SCM metadata and
  EPL-2.0 license metadata. The project URL is
  `https://github.com/hugoduncan/value-type`.
- Add the full EPL-2.0 text as the root `LICENSE` file and include it in
  the JAR. Identify the license in the README.
- Each successful JAR build starts with a clean `target/` directory so
  that stale files cannot enter the artifact. The clean command removes
  `target/` and does not require Git metadata. Document that both commands
  can remove existing benchmark output under `target/`.
- Keep build dependencies separate from runtime dependencies.
- Document the build commands, artifact path, version rule, Git
  requirement and source-only contents. Keep the distinction between
  local artifact creation and a published release clear.

## Design choice and constraints

Use a tools.build entry point, as in the reference, rather than a new
packaging framework. Keep the build small and specific to this library.
The build owns only generated output under `target/`; it must not change
library sources or runtime behavior.

A source-only JAR avoids binding packaged classes to one preview JDK
release. It does not remove the consumer requirement for a JEP 401
Valhalla JDK with `--enable-preview`. State that requirement in the build
documentation. Packaging must not load or AOT-compile library namespaces.

The Git commit count is the requested version rule, not a unique release
identifier across branches. Do not add tags, release automation or other
version policies in this task.

## Acceptance criteria

1. From a checkout with Git history, `clojure -T:build jar` succeeds and
   produces the specified artifact. Its reported version, filename and
   Maven metadata agree with `0.1.<git rev-list --count HEAD>`.
2. Archive inspection confirms that all library source files, the full
   license text and Maven metadata are present. No compiled classes,
   bundled dependencies or development files are present. A stale file
   placed under `target/` before the build is absent from the artifact.
3. Maven metadata identifies `org.hugoduncan/value-type`, the project
   description, URL, SCM and EPL-2.0 license. Its dependencies match the
   project runtime dependencies; build, test, benchmark, REPL and
   user-level dependencies are absent.
4. An isolated consumer JVM uses the built JAR and its declared runtime
   dependencies, without the repository source directory on its
   classpath. On a supported Valhalla JDK with `--enable-preview`, it
   requires `value-type.core`, defines a value type, constructs instances
   and verifies that the generated class is a value class.
5. A failed Git invocation or invalid commit-count output fails the JAR
   build with a clear diagnostic. No fallback version is used.
6. `clojure -T:build clean` removes `target/`, succeeds when `target/` is
   absent, and works without Git metadata.
7. Automated checks cover the build contract and consumer use. Existing
   library tests still pass on the supported Valhalla JDK.
8. The README supplies the commands and requirements above, identifies
   EPL-2.0, and does not claim that an artifact has been published.

## Out of scope

- AOT compilation, an executable JAR or an uberjar.
- Local Maven installation, Clojars publication, signing or credentials.
- CI release workflows, release tags or a new version policy.
- Changes to `defvaluetype` or its supported JVMs.
- Byte-for-byte reproducible archives.

Installation and publication can be separate tasks if needed.
