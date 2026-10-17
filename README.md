# Zipit

Zipit is an ASM-based Java bytecode obfuscator with a CLI, Gradle/Maven
plugins and a Swing GUI. It combines renaming, string and constant encryption,
control-flow transforms, method virtualization, anti-LLM countermeasures,
reference hiding, watermarks, audit reports and RSA-signed licensing in one
deterministic pass pipeline.

Main capabilities:

- Class, method and field renaming with keep rules and ProGuard mappings
- String, integer and long encryption; control-flow obfuscation and flattening
- **Method virtualization**: eligible method bodies are translated into a custom
  instruction stream and executed by an injected stack-machine interpreter
- **Anti-LLM**: semantic decoys, guarded decoy calls, nested forwarding walls
  and encrypted VM payload layers
- Reference hiding via invokedynamic, member trimming, parameter mutation,
  constructor wrapping and exception noise
- Automatic reflection keep analysis and framework presets
- Audit reports, stable mappings, incremental builds, watermark tracing and
  JVM verification
- RSA-SHA256 licenses and a self-hosted license server

## Requirements

- JDK 8+ (the build and full test suite have run on JDK 8, 21 and 26)
- The Gradle wrapper is included; `build.sh` / `build.bat` use it first and
  fall back to a system or cached Gradle 8.x installation.

## Build

```bash
# Linux / macOS
./build.sh build

# Windows
build.bat build

# Also produce the executable fat jars under dist/
./build.sh dist
```

Artifacts:

```text
dist/zipit-1.0.0-all.jar               # CLI
dist/zipit-gui-1.0.0-all.jar           # GUI
dist/zipit-license-server-1.0.0-all.jar
dist/zipit.example.yml
```

## Command line

```bash
java -jar dist/zipit-1.0.0-all.jar \
  -i app.jar -o app-obfuscated.jar \
  --keep com.example.Main \
  --seed 23303 \
  --verify
```

Using a YAML configuration:

```bash
java -jar dist/zipit-1.0.0-all.jar -c zipit.example.yml
```

Common options:

```text
-c, --config <file>       YAML configuration
-i, --input <jar>         input JAR
-o, --output <jar>        output JAR
--lib <path>              library JAR or directory (repeatable)
--keep <pattern>          keep rule (repeatable)
--preset <name>           library | spring-boot | jackson | service-loader | micronaut | aggressive
--seed <value>            deterministic seed
--virtualize              enable method virtualization
--anti-llm                enable anti-LLM decoys, wrapper walls and payload shielding
--flatten                 enable control-flow flattening
--verify                  verify every output class with the JVM
--require-license         exit with code 3 when the license is invalid
```

## Package obfuscation

Renaming class names is not enough: unchanged package paths such as
`com/example/module/combat` reveal functionality. Enable package obfuscation to
map every original package to a random short package:

```yaml
renamer:
  enabled: true
  repackage: true
  repackagePackage: "*"
```

Classes that must stay at a framework boundary (`--keep` or annotation keep
rules) remain in place; all other classes move. Package-private access is
preserved because classes from the same original package move together.

For maximum hiding, kept boundaries can still receive a package-only move
without changing their members or bytecode:

```yaml
renamer:
  repackage: true
  repackagePackage: "*"
  repackageKept:
    - "com.example.script.**"
    - "com.example.inject.**"
```

Entry points loaded by `Class.forName` can be renamed too. Enable string
rewriting so the literal is updated instead of forcing a keep:

```yaml
reflection:
  enabled: true
  rewriteClassStrings: true
```

Asset directory names such as `assets/clickgui` or `assets/keystrokesmod` can
be renamed, with matching path strings rewritten in code:

```yaml
resources:
  obfuscatePaths: true
  rewriteDomains:
    - "keystrokesmod"
```

`rewriteDomains` lists bare domain strings used by `ResourceLocation`-style
lookups. Keep a domain out of this list if the same string is also a mod id or
a user-visible command.

If the control-flow flattener hits an unsupported local-variable shape in a
class, list that class under `flowObfuscation.flatteningExclude`; the class
still receives renaming, encryption, virtualization and anti-LLM transforms,
but not flattening:

```yaml
flowObfuscation:
  flattening: true
  flatteningExclude:
    - "com.example.ChatCommands"
```

## Method virtualization

```bash
java -jar dist/zipit-1.0.0-all.jar \
  -i app.jar -o app-obf.jar \
  --virtualize --verify
```

Or in YAML:

```yaml
methodVirtualization:
  enabled: true
  maxInstructions: 2000
  maxMethods: 0        # per-class cap; 0 = unlimited
```

Safe subset: methods containing `NEW` (object construction), try/catch,
`invokedynamic`, monitors or `JSR/RET` are skipped automatically. Constant
strings are encrypted with a per-call-site key, including constants stored in
virtualization payloads.

## Anti-LLM

```bash
java -jar dist/zipit-1.0.0-all.jar \
  -i app.jar -o app-obf.jar \
  --virtualize --anti-llm --verify
```

```yaml
antiLlm:
  enabled: true
  decoyMethodsPerClass: 2
  decoyCallSitesPerClass: 2
  wrapperLayers: 1
  maxWrappedMethodsPerClass: 12
  shieldVirtualization: true
```

This adds semantic decoy methods, guarded decoy calls, nested forwarding
walls and encrypted/fragmented VM payloads. It raises the cost of automated
static analysis; it is not a proof against dynamic debugging or human review.

## License server

Start a local server (defaults to `127.0.0.1:8080`):

```bash
java -jar dist/zipit-license-server-1.0.0-all.jar \
  --data ./license-data \
  --admin-token s3cret \
  --open
```

For other users, bind to a reachable address and declare the public URL:

```bash
java -jar dist/zipit-license-server-1.0.0-all.jar \
  --host 0.0.0.0 --port 8080 \
  --public-url https://license.example.com \
  --admin-token s3cret
```

Each client points at its own deployed server. Three equivalent ways:

```yaml
license:
  enabled: true
  file: "license.yml"
  publicKey: "public.pem"
  server: "https://license.example.com"
  require: true
```

```bash
--license license.yml --license-key public.pem \
  --license-server https://license.example.com --require-license
```

```bash
export ZIPIT_LICENSE_SERVER=https://license.example.com
```

The admin page at `http://<host>:<port>/` shows copy-ready client YAML and
command snippets. `GET /api/server-info` returns the configured public URL.
Clients verify the RSA signature locally and use the server for revocation and
online validation.

## GUI

```bash
java -jar dist/zipit-gui-1.0.0-all.jar
```

The GUI edits the same configuration schema as the CLI. The Transforms page
includes method virtualization and anti-LLM controls; the Enterprise page
accepts license files, public keys and the license server URL. **File > Save
configuration** produces a YAML file that can be replayed in CI with `-c`.

## Integration tests

Real Hibernate 5.6 and 7.4 + H2 projects are under `integration/`. They build
an application, obfuscate it with virtualization and anti-LLM enabled, verify
the output and run it on JDK 8, 21 and 26:

```bash
cd integration/hibernate-legacy
gradle run
gradle obfuscate
gradle runObfuscated
```

See `integration/README.md` for details.

