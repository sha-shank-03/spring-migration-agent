# Local contracts and artifacts

These are project-local Java/Gson contracts, not MCP or a claimed external standard. Versions describe currently implemented fields; runtime validation, limits and trust boundaries are in the source. JSON reports are evidence to cross-check against physical files, XML and logs, not authenticated attestations.

| Contract | Meaning |
| --- | --- |
| Target `boot-2.7.18-to-3.0.13` | Exact historical unsupported Boot parent pair, Java 17, declared single-module MVC/validation Maven surface |
| `springmig.http-contract/v1` | External `checks`: `id`, GET/POST `method`, local `path`, optional JSON `body`, expected HTTP status and optional exact body |
| `springmig.agent/v1` | Request mode/target/recipe/contract hashes, stage/version/build/test diagnostics, exact sorted source manifest and aggregate digest |
| `springmig.transcript/v1`, kind `recorded_mock` | Up to three entries of request, request SHA-256 and proposal or null finish response |
| `springmig.report/v2` | Target/provenance, labels, budgets, actual commands/stages, accepted/unaccepted proposals, integrity/digests and stopping state |
| `springmig.comparison/v2` | Mock harness matrix with actual per-mode exits and identical snapshot checks; expected fixture outcome is distinct from every mode passing |
| `springmig.event/v1` | JSONL verification events containing actual stage results; it is not a complete tool audit stream |
| `springmig.cache/v1` | Online preparation status and explicit requirement for subsequent offline proof |

`Proposal` is a list of `Edit(relativePath, preimageSha256, before, after)` plus a nonempty reason. Each replacement requires one unique, nonempty exact `before` block and a bounded replacement. No shell or test-edit tool is exposed. The gateway checks all paths/preimages and authority before staging. Successful, declined, no-op and policy/budget outcomes remain distinguishable in reports.

Each captured request hashes the exact Gson serialization using UTF-8 and the configured pretty/null/HTML-escaping settings in `Json`. The source aggregate hashes sorted entries encoded as `path + NUL + sha256 + LF`. Transcript replay checks both the recorded request's hash and equality to the new canonical request, rejects unused/exhausted entries, and still applies every proposal through the gateway. Contract-relevant response bodies are included when the actual status equals its expected status and the contract specifies a body. Unspecified, timestamp-bearing error bodies are excluded from the request; full observations remain in `report.json`. This is source/request consistency, not transcript authentication. Fresh execution is labelled `scripted_mock`; replay is `recorded_mock_replay`; recipes-only is `none`. All have `liveModelCalls=0`.

## Enforced bounds

At most three proposal attempts and twenty charged gateway actions; flags can reduce them. Reads are at most 64 KiB; aggregate changed file contents per proposal are at most 256 KiB. Inputs allow at most 500 files/1 MiB per file. Retained subprocess logs are capped at 1 MiB while output continues to drain. Commands use at most 180 seconds; the run at most 900 seconds, with remaining time supplied to tools/HTTP and bounded cleanup/evidence after expiry. All tool JVMs and test forks explicitly use 2 GiB heaps. These are configured limits, not measured peak RSS. Replay files are at most 1 MiB and three entries.

| Exit | Meaning |
| --- | --- |
| 0 | Fresh final verification passed |
| 2 | Final build/behavior verification failed |
| 3 | Unsupported CLI/input/preflight |
| 4 | Tool, cache, command timeout or cleanup failure |
| 5 | Run/action/proposal budget exhausted |
| 6 | Policy, replay or integrity denial |
| 7 | Baseline verification failed |
| 8 | Interrupted run |

Expected recipes-only exit 2 must remain visible. `compare` returns 0 only when its named fixture's expected matrix and identical snapshots hold. Agent-only's initial target compilation intentionally fails before the mechanical mock; this stage executes zero tests. Successful builds execute the three original tests. Infrastructure failures are not treated as unsupported-source repair opportunities.

## Review outputs

`migration.patch` spans baseline to current candidate; `recipe.patch` spans the mechanical stage and is empty for agent-only; `agent.patch` spans recipe result to repaired candidate, or baseline to agent-only result. `report.json`, `report.md`, `events.jsonl`, bounded logs, protected inputs and distinct verification trees retain observations. Some early failure paths cannot create all ordinary patches/stages, and forceful OS termination cannot guarantee a report. CLI help retains its M2 marker because M3 adds delivery tooling without changing runtime behavior.

`scripts/demo.ps1` asserts exits, labels, offline argv, physical source/protected hashes and Surefire XML, literal HTTP results, distinct final trees, replay equality, cold-cache/timeout/mismatch failures, patch byte equality and native process cleanup. `-Extended` also asserts mechanical/unresolved fixtures. These are worker/CI checks. Main independent acceptance remains separate. `scripts/package-review.ps1` creates a local review ZIP plus per-file hashes; it excludes tool caches and generated Spring application JARs and includes CLI notices alongside the executable.
