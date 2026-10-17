# Integration matrix

Real framework projects used to verify Zipit against actual dependencies. Each
project builds a runnable JAR, obfuscates it with `dist/zipit-1.0.0-all.jar`,
and runs the obfuscated output.

## Hibernate

| Project | Dependency | JDK |
|---|---|---|
| `hibernate-legacy` | Hibernate 5.6.15 (`javax.persistence`) + H2 2.2.224 | 8 (build/run), 21 / 26 (run output) |
| `hibernate-modern` | Hibernate 7.4.13 (`jakarta.persistence`) + H2 2.3.232 | 21 (build/run), 26 (run output) |

Both samples:

- create the schema, persist two `Person` rows, run an HQL query and commit;
- keep the `@Entity` class through `--preset jackson`;
- rename, string-encrypt, virtualize and anti-LLM-transform the plain `Score`
  business class;
- verify every output class with Zipit's `--verify`.

The `gradle` commands below assume Gradle 8.x is on `PATH`; the same Gradle
distribution used by `./build.sh` works as well.

```bash
# From the Zipit repository root
./build.sh dist

cd integration/hibernate-legacy
gradle run
gradle obfuscate
gradle runObfuscated

cd ../hibernate-modern
gradle run
gradle obfuscate
gradle runObfuscated
```

`runObfuscated` reads the renamed main class from the output JAR manifest, so it
keeps working after renaming.

Expected obfuscation statistics:

```
anti-llm: 4 decoy methods, 3 guarded call sites, 3 wrapper layers across 2 classes
method-virtualization: virtualized 11 methods (590 instructions), 0 skipped
Verification: OK
```

Expected output (the order/ids are stable):

```
legacy:1:Ada:42:adult
legacy:2:Linus:55:senior
legacy-total:97
hibernate-legacy-ok
```

To run an obfuscated JAR on another JDK, use the runtime classpath printed by
`gradle -q printClasspath`:

```bash
java -cp "build/libs/hibernate-modern-demo-obf.jar;<runtime-classpath>" hibdemo7.a
```

## Adding a framework

1. Compile the application and run it unchanged.
2. Run Zipit with the appropriate preset and `--verify`.
3. Run the obfuscated JAR and compare output.
4. Record the dependency versions and JDK in this file.
