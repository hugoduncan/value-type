# Valhalla spike

Throw-away code. It verifies the assumptions of
`ramora/defvaluetype-design`. The results are in
`ramora/defvaluetype-design/verification.md`.

JDK: `openjdk-27-jep401ea3+1-1` (macOS aarch64), from
https://jdk.java.net/valhalla/, installed at
`~/.local/jdks/jdk-27.jdk/Contents/Home`.

```sh
export JAVA_HOME=~/.local/jdks/jdk-27.jdk/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH

# Java reference class and reflection probe
javac --enable-preview --release 27 -d /tmp/vt Point.java Probe.java
java --enable-preview -cp /tmp/vt Probe

# Clojure: require path, then REPL eval paths
clojure -J--enable-preview -M run.clj
clojure -J--enable-preview -M repl.clj

# AOT
clojure -J--enable-preview -M -e "(compile 'spike.use)"
```

`src/spike/vt.clj` has a working transform, capture loader and macro.
Use it as the start point for the implementation, not as the
implementation.
