# Interface-call investigation

See [report](../../ramora/defvaluetype-design/interface-call.md).
This is a diagnostic spike, not a performance gate or library code.

Run from the repository root with the recorded Valhalla JDK installed:

```sh
bash spike/interface-call/run.sh
bash spike/interface-call/diagnose.sh
python3 spike/interface-call/extract-jit.py
```

Set `JDK` to override the JDK home. The default is
`$HOME/.local/jdks/jdk-27.jdk/Contents/Home`.
Each command accepts an optional output directory (default:
`target/interface-call`). Repeating a command replaces its named outputs.
Run the commands in order, with no other benchmarks running.
The extraction script needs macOS arm64, Python 3 and Xcode command-line tools.

- `InterfaceControl.java`: Java identity/value pair and separate pass loops.
- `run.clj`: Criterium driver with sum and representation assertions.
- `run.sh`: 16 sequential fresh JVMs, alternating order, two array lengths.
- `diagnose.sh`: two extra JVMs with compilation logs and targeted code dumps.
- `extract-jit.py`: retains relevant JIT tasks and disassembles final C2 code.

The timing runs retain per-JVM medians and batch sizes, not all Criterium
samples. Diagnostic timings are not timing evidence. The driver reuses
private benchmark helpers; this is intentional for this bounded investigation.
