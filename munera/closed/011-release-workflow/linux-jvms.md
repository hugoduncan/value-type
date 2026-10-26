# Linux JVM verification

Slice 1 verified these Linux x86_64 archives on 2026-10-26. Keep these versions;
do not replace the Valhalla EA with a different bytecode format.

| JVM | Archive URL | SHA-256 |
| --- | --- | --- |
| Temurin 8 | https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u492-b09/OpenJDK8U-jdk_x64_linux_hotspot_8u492b09.tar.gz | `da257f161d7f8c6ca5b0e5d9e4090f65ac28c5e398072e68b8ae87988b1d1a2e` |
| Valhalla | https://download.java.net/java/early_access/valhalla/27/1/openjdk-27-jep401ea3+1-1_linux-x64_bin.tar.gz | `b8bdd7b181c6a5ea2dd9959255e222cd9d9a9f42cca4f2400991b9b2ff7ffb7d` |

Checksums came from the Adoptium version API (`8u492-b09`, Linux x64,
HotSpot JDK) and the Valhalla archive's `.sha256` URL. Both downloaded archives
passed `sha256sum -c` inside the container before extraction.

## Reproduction

Download the archives as `java8.tar.gz` and `valhalla.tar.gz`. Check them against
the hashes above. In Linux, create `/jvms/java8` and `/jvms/valhalla`, then run:

```sh
tar -xzf java8.tar.gz -C /jvms/java8 --strip-components=1
tar -xzf valhalla.tar.gz -C /jvms/valhalla --strip-components=1
/jvms/java8/bin/java -version
/jvms/valhalla/bin/java -version
```

For CI, use the same extraction layout under `RUNNER_TEMP` rather than `/jvms`.
Set `VALUE_TYPE_BASELINE_JAVA` and `VALUE_TYPE_VALHALLA_JAVA` to the corresponding
`bin/java` paths. Select `JAVA_CMD` separately for each suite.

The local verification used `podman run --rm --platform linux/amd64` with
`ubuntu:24.04` (image config `6277ab668b2ad3c3ab851d370faa1690d324b30db29d342527f61fed28f20f36`).
`uname -m` returned `x86_64`. This was emulated on an Apple Silicon host, not a
native GitHub runner. Extract into the container filesystem: extraction onto
the macOS shared mount failed on a Java 8 symlink.

Mount checkout `src` read-only at `/src`. Supply exactly the runtime POM's JARs
at `/archives/lib`: Clojure 1.12.6, spec.alpha 0.5.238 and core.specs.alpha 0.4.74.
The capability check was:

```sh
/jvms/valhalla/bin/java --enable-preview \
  -cp '/archives/lib/clojure-1.12.6.jar:/archives/lib/spec.alpha-0.5.238.jar:/archives/lib/core.specs.alpha-0.4.74.jar:/src' \
  clojure.main -e '(require (quote value-type.impl.capability))
    (let [result (value-type.impl.capability/detect)]
      (prn result) (assert (:available? result)))'
```

## Results

The combined checksum, extraction, version and probe command exited 0:

```text
java8.tar.gz: OK
valhalla.tar.gz: OK
openjdk version "1.8.0_492"
OpenJDK Runtime Environment (Temurin)(build 1.8.0_492-b09)
OpenJDK 64-Bit Server VM (Temurin)(build 25.492-b09, mixed mode)
openjdk version "27-jep401ea3" 2026-09-15
OpenJDK Runtime Environment (build 27-jep401ea3+1-1)
OpenJDK 64-Bit Server VM (build 27-jep401ea3+1-1, mixed mode, sharing)
{:available? true}
```

This uses the real library capability probe, including class definition,
constructor invocation, field access and `Class.isValue`. No mock or new
production path was added. Full runtime suites and hosted execution remain
for later slices. An initial wildcard classpath included incompatible cached
spec versions; the exact POM-derived versions above resolved that local error.
