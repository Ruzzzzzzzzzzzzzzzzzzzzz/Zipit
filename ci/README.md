# CI integration

These files are templates; adjust the application JAR paths and keep rules for
your project.

## Artifacts to retain

| Artifact | Why |
|---|---|
| `app-obfuscated.jar` | protected application |
| `zipit-mapping.txt` | de-obfuscate stack traces and keep names stable across releases |
| `zipit-audit.json` / `.html` | audit trail for release review |
| license file (if used) | enterprise authorization |

The mapping file is written next to the output JAR by default. Pass
`<inputMapping>` / `--input-mapping` on the next build to keep existing names
stable even when other inputs change.

## Exit codes

| Code | Meaning |
|---:|---|
| 0 | success |
| 1 | configuration / transform failure |
| 2 | `--verify` found a class that does not link |
| 3 | `--require-license` and the license is missing, expired or invalid |

## Verification

`--verify` loads every output class with `Class.forName(..., false, loader)` and
forces linking via `getDeclaredMethods()`. This catches stack-map, descriptor
and access errors before deployment. It is intentionally separate from running
the application; application-level smoke tests remain the responsibility of the
project.

## Reproducible builds

Use a fixed `seed`, keep the same input, and optionally `--fixed-time`. The
same input + config + seed produces byte-identical output.
