"""Extract relevant JIT tasks and disassemble final C2 pass methods on macOS.

Run after diagnose.sh. Uses the Xcode command-line tools, not hsdis.
The object file preserves instruction bytes but not their original address.
"""
import pathlib
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

out = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "target/interface-call")
for mode in ("clojure", "java"):
    root = ET.parse(out / f"{mode}-compilation.xml").getroot()
    names = (("interface_dt invokeStatic", "interface_vt invokeStatic")
             if mode == "clojure" else
             ("InterfaceControl identityPass", "InterfaceControl valuePass"))
    tasks = [t for t in root.iter("task")
             if any(n in t.get("method", "") for n in names)]
    with (out / f"{mode}-jit-tasks.xml").open("w") as stream:
        stream.write("<tasks>\n")
        for task in tasks:
            stream.write(ET.tostring(task, encoding="unicode") + "\n")
        stream.write("</tasks>\n")

    log = (out / f"{mode}-assembly.log").read_text()
    blocks = re.split(r"(?=Compiled method \()", log)
    for kind, method in (("dt", "interface_dt"), ("vt", "interface_vt")) if mode == "clojure" else (("dt", "identityPass"), ("vt", "valuePass")):
        candidates = [b for b in blocks if b.startswith("Compiled method (c2)")
                      and method in b.splitlines()[0]
                      and " % " not in b.splitlines()[0]]
        block = candidates[-1]
        start, end = [int(x, 16) for x in re.search(
            r"main code\s+\[(0x[0-9a-f]+),(0x[0-9a-f]+)\]", block).groups()]
        code = bytearray()
        for line in block.splitlines():
            match = re.match(r"\s*(0x[0-9a-f]+): ([0-9a-f]{4}.*)", line)
            if match:
                address = int(match[1], 16)
                if start <= address < end:
                    assert address == start + len(code), (address, len(code))
                    code.extend(bytes.fromhex(match[2].replace("|", " ")))
        assert len(code) == end - start
        stem = out / f"{mode}-{kind}-c2"
        stem.with_suffix(".s").write_text(
            ".text\n.globl _jit_code\n_jit_code:\n" +
            "\n".join(".byte " + ",".join(str(x) for x in code[i:i+16])
                      for i in range(0, len(code), 16)) + "\n")
        subprocess.run(["clang", "-arch", "arm64", "-c", str(stem.with_suffix(".s")),
                        "-o", str(stem.with_suffix(".o"))], check=True)
        disassembly = subprocess.check_output(
            ["objdump", "-d", str(stem.with_suffix(".o"))], text=True)
        stem.with_suffix(".txt").write_text(
            f"Original start: {start:#x}; main code bytes: {len(code)}\n" + disassembly)
        print(mode, kind, len(code), stem.with_suffix(".txt"))
