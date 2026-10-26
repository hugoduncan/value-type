# Cause of the extra 8 bytes

## Finding

The extra space comes from the Valhalla VM, not from `defvaluetype` or
Criterium. On JDK `27-jep401ea3+1-1`, the VM adds a **1-byte null marker**
to these nonempty value objects. Object alignment then adds **7 bytes of
padding**. The total increase is 8 bytes.

This investigation used the same macOS/aarch64 JDK as the
[original comparison](object-size.md). It checked VM field layouts,
repeated native allocation measurements and measured plain Java controls
with `Instrumentation.getObjectSize`.

## Layout evidence

For one `long`, `-XX:+UnlockDiagnosticVMOptions -XX:+PrintFieldLayout`
reports:

| Offset | Identity object | Value object |
| ---: | --- | --- |
| 0–11 | 12-byte header | 12-byte header |
| 12–15 | Alignment gap | Alignment padding |
| 16–23 | `long f0` | `long f0` |
| 24 | — | `NULL_MARKER`, 1 byte |
| 25–31 | — | End padding |
| Total | 24 bytes | 32 bytes |

The marker is at offset 32 for two longs and offset 40 for three longs.
The header size and user-field offsets do not change between each pair.
The VM also adds static fields, but those do not contribute to the shallow
instance size.

For the empty value class, the VM inserts a 1-byte `.empty` field at offset
12 and reuses it as the null marker. It fits inside the 16-byte minimum
object size. Thus the empty pair has no size difference. These VM-injected
details were not visible in the benchmark's reflection field checks.

## Why a standalone object contains a null marker

A nullable flattened value needs to distinguish `null` from a value whose
fields are all zero. This VM prepares such a layout for these classes.
It also uses that layout for standalone heap instances, so their payload
can be copied to another container with the marker already set to non-null.
This does not mean that a standalone object is itself null.

The marker must be inside the copied payload. For the all-long cases, the
payload starts at offset 16. The gap at offsets 12–15 is outside it and
cannot hold the marker. There is no gap between the longs, so the marker
is appended.

This is explicit in the matching OpenJDK source:
[`fieldLayoutBuilder.cpp`, commit `f9799f4c1a35`](https://github.com/openjdk/valhalla/blob/f9799f4c1a35/src/hotspot/share/classfile/fieldLayoutBuilder.cpp).
The JDK's `release` file identifies that commit.

- Lines 1217–1271: select an internal gap or append a 1-byte null marker.
- Lines 1309–1317: use the nullable layout for standalone heap instances.
- Lines 1542–1551: round instance size to the VM's object alignment.

Retrieved source SHA-256:
`668549a339050b01ce8097fb94b1495ab9bfb7d4e1adedceaceaa3d1e7006871`.

## Controlled check

Fresh JVMs were run with the default preview settings and with only
`-XX:-UseNullableNonAtomicValueFlattening` added.

| Long fields | Identity, both runs | Value, default | Value, flag disabled |
| ---: | ---: | ---: | ---: |
| 0 | 16 | 16 | 16 |
| 1 | 24 | 32 | 24 |
| 2 | 32 | 40 | 32 |
| 3 | 40 | 48 | 40 |

The changed flag removed the marker from all three nonempty value layouts.
The existing Criterium runner validated ten allocation records per class
in each run. Plain Java identity/value class pairs gave the same sizes
through a separate Java instrumentation agent. Agent-free Clojure layout
runs also agreed. Thus neither the library transform nor Criterium causes
the extra space.

With preview enabled, both nullable flattening flags are true by default.
For these nonempty long-only classes, the nullable atomic layout is not
available; disabling nullable **non-atomic** flattening is enough to remove
the marker. This is a diagnostic check, not a recommended application
setting: it changes VM flattening choices. Do not infer preview defaults
from `PrintFlagsFinal` without `--enable-preview`.

## Reference fields

The [reference-field follow-up](object-size-references.md) also found +8
bytes for 1–3 `Object` fields with defaults. However, the one-reference
case retains its marker when only non-atomic nullable flattening is disabled.
Atomic nullable layout and payload alignment matter in that case.

## Reproduction and evidence

Run `bash spike/object-size/run.sh` from the repository root.
See [probe instructions](../../spike/object-size/README.md).

Retained evidence is in [object-size-investigation](object-size-investigation/):
`default/` and `no-nullable-non-atomic/` each contain full raw allocation
records, validated results, logs, and complete target-class layout blocks.
`jdk-release.txt` and `preview-flags.txt` record the build and defaults.
The script leaves full layout logs in `target/object-size-investigation/`.

Production code and the original measurement evidence are unchanged.
The result applies to these standalone objects on this build. Other field
layouts can contain usable gaps or different alignment needs. Flattened
arrays, other JDK builds and other header settings need separate checks.
