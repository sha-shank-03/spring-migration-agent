# Final M3 review evidence

Date: **2026-10-08**. Repository: `C:/Users/shash/Documents/CodexPortfolios/agentic-ai-2026-10-07/spring-migration-agent`. Windows, existing Microsoft Java 17.0.18, Maven 3.9.11 and Git 2.45.2.windows.1. The final default-shell inventory observes bundled PowerShell **7.6.5**; the build/demo receipts do not separately record a shell version. Windows PowerShell 5.1 was not separately validated. Historical unsupported Boot 2.7.18 to 3.0.13; trusted fixtures only. No downloads, new runtime dependencies, live model calls, secrets, publication or deployment in M3.

Status: **M1 and M2 independently accepted by main; final assigned M3 delivered for whole-repository acceptance**. M3 adds reusable asserting review/packaging scripts, quickstarts, architecture/contracts, attribution and prepared CI. The runtime, POM, wrappers, recipe, contracts, tests and fixtures remain byte-identical to accepted M2: all **41 files** in `outputs/m3-static/runtime-before-verification.json` match the after-demo hashes in `outputs/m3-static/final-preservation-and-processes.json`. Worker M3 success is not main final acceptance.

## Main independent acceptance already obtained

Main's `coordination/reviews/spring-migration-agent-M3.md` records M2 as independently passed on 2026-10-08: actual offline Maven verification with 18 tests; fresh three-mode comparison with 14 Maven `-o` invocations and literal 2/0/0 mode exits; fresh recorded replay matching the candidate; patch application recreating all six candidate files; and empty-cache exit 4. Main checked physical source/protected/candidate hashes, actual Surefire XML with three tests per successful verification build, distinct fresh final workspaces, literal HTTP 5/6 versus 6/6, and native zero owned JVMs.

Main receipts are outside this repository under `coordination/acceptance/spring-migration-agent/`: `m2-main-build.json`, `m2-main-comparison.json`, `m2-main-replay.json`, and `m2-main-probes-20261008/summary.json`. Main's original build stdout was truncated; its receipt uses observed exit 0 / BUILD SUCCESS and actual generated XML hashes/totals. It does not claim a full build log. Main retained, rather than reran, the worker's full mechanical/unresolved/interruption/final-failure matrix below. Main-owned review, acceptance and resource files were read only.

## Fresh worker M3 execution

The named `spring-migration-agent` assigned-worker lease granted at `2026-10-08T02:24:13.266911Z` permits exactly one clean offline build followed by one fresh core offline demo, sequentially using the existing cache. Both authorized operations completed; no extra build or integration rerun was made.

```powershell
.\mvnw.cmd -o clean verify
.\scripts\demo.ps1 -Offline -Out outputs/m3-review
.\scripts\package-review.ps1 -Out outputs/m3-package
```

The first command exited **0** with **18 tests, zero failures/errors/skips**. Exact argv/cwd/exit/start/end are in `outputs/m3-build/command.json`; complete stdout is `outputs/m3-build/build.log`; copied actual XML/text are `outputs/m3-build/surefire-reports/`; totals and built CLI SHA-256 are `outputs/m3-build/results.json`. CLI SHA-256: `b5f95c2fd5f9ae0d9622375ac9adda5e75468128fa29712a1927930d2adc7c8f`.

The initial report-copy command omitted recursion and copied an empty directory. Its evidence-count assertion failed after the successful build; report files were then copied correctly without rebuilding. `outputs/m3-build/evidence-copy-correction.json` preserves the error and correction. This was a receipt-copy failure, not a Maven/test failure.

The fresh demo exited **0** under expected-outcome assertions. Its raw stdout and outer receipt are `outputs/m3-build/demo.log` and `demo-command.json`. Each individual CLI command has an expanded argv/cwd/expected/actual exit/log/time receipt under `outputs/m3-review/`; the summary is `outputs/m3-review/summary.json`. Individual failing CLI exits are preserved.

| Fresh case under `outputs/m3-review/` | Actual CLI outcome | Accepted proposals | Real offline Maven commands |
| --- | --- | --- | --- |
| `comparison/recipes-only` | 2, `verification_failed`; final HTTP 5/6 | 0 | 4 |
| `comparison/agent-only` | 0, `verified`; no recipe; final HTTP 6/6 | 2 | 5 |
| `comparison/hybrid` | 0, `verified`; final HTTP 6/6 | 1 | 5 |
| `replay with spaces` | 0, `recorded_mock_replay`; final HTTP 6/6 | 1 | 5 |
| `cache-miss` | 4, `offline_cache_miss`; actual empty scoped cache | 0 | 1 |
| `timeout` | 4, `command_timeout`; actual one-second Maven timeout | 0 | 1 |
| `replay-mismatch` | 6, `policy_denied`; behavior-preserving source comment invalidates recording | 0 | 3 |

All three modes use snapshot `51e7259f1a78ffc82f632bbe85ea6e4c66775ba9e2dfa34881963d68c5355107`. Agent-only, hybrid and replay have the same final candidate digest `6d25fb03c5fd573579575e668a214e8b2ecf279908bd2bb159d73b0d18567195`. Seven physical reports record **24 actual Maven `-o` invocations** with cleanup complete. The checker reads actual protected/source/candidate bytes and Surefire XML; it requires three preserved tests per successful build, literal HTTP results, clean/no-skip flags, direct/test-fork 2 GiB caps, distinct verification trees and a successful fresh final stage for every verified run. Expected compilation failure executes zero tests and is not represented as a passed build.

Fresh patch review used `outputs/m3-review/patch-review`, a standalone Git fixture with `core.autocrlf=false`. `git apply --check` and `git apply` exited 0; the resulting six-file tree digest equals the verified hybrid candidate. A separate rebuild of that byte-identical patch-applied tree was not repeated. The original fixture stayed intact. Native process inventory after the demo found **0 repository-owned JVMs**; `summary.json` and `final-preservation-and-processes.json` retain these observations. Peak RSS and network traffic were not measured.

The first descriptive preservation receipt searched nonexistent report fields for Maven/proposal counts. The retained `prior-report-field-mapping-error.json` is superseded by `final-preservation-and-processes.json`, which counts actual command argv plus `stages.build`, `repairProposals` and `finalCandidateSha256`. Raw reports and demo results were unchanged.

## Static verification, attribution and package

`work/research/check-m3-static.py` uses pre-existing Python 3.12.7 / PyYAML 6.0.3; no install occurred. `outputs/m3-static/workflow-links-attribution.json` records parsed workflow, immutable action pins, read-only permissions/checkout, serial matrix and 45-minute limit, absence of configured secret/publication features, zero broken relative documentation links, and SHA-256/byte/license-source matches for **399 actual cached JARs**. PowerShell AST parsing reports zero errors in the review/demo/packaging scripts. The catalog represents the tested Windows M1/M2 cache, not 399 distributed CLI dependencies, vulnerability verification or a Linux resolution result.

The new checker also passed against retained M2 main-mode/replay/failure artifacts (`retained-artifact-assertions.json`); retained mechanical/unresolved results were inspected. These are static cross-checks of older execution, not fresh M3 integration runs. Deliberately corrupted copied evidence was rejected: wrong literal HTTP body, changed protected expectation and changed candidate bytes. Cases/reasons are in `outputs/m3-static/negative-assertion-results.json`; originals were untouched.

`third-party/dependency-inventory.json` records all 399 coordinates, portable paths, bytes/SHA-256, declared/inherited licenses, actual upstream POM attribution URLs and conventional artifact/source-archive candidate URLs. Source-archive availability was not tested or downloaded. `bootstrap-integrity.json` preserves actual M1 wrapper/Maven checksum records. Existing Gson/Error Prone/wrapper notices and exact pinned action MIT license texts accompany project Apache-2.0 source. `DEPENDENCIES.md`, `NOTICE` and `docs/SOURCES.md` distinguish bundled CLI components from excluded build/recipe/test tools, including non-Apache declarations.

The local packaging command above stages a whitelist of source, built CLI, wrappers, fixtures, docs, catalog and notices. It excludes Git metadata, Maven cache/distributions, generated Spring application JARs and large raw workspaces. `outputs/m3-package/source-and-cli-manifest.json` lists each source/CLI SHA-256 and byte count; `package-receipt.json` records the ZIP hash/bytes, entry checks and local-only state. The packaging script requires every ZIP source/CLI entry to hash identically to its original. The raw command/exit/log receipt is `outputs/m3-build/package-command.json` / `package.log`. Package verification covers archive bytes; it does not claim another executable or patch rebuild.

## Prepared CI and checks not executed

`.github/workflows/verify.yml` is prepared locally and not pushed/dispatched. Official action tag APIs read on 2026-10-08 map checkout v7.0.1 to `3d3c42e5aac5ba805825da76410c181273ba90b1` and setup-java v6.0.1 to `de7274f081f381c8f8158605e0321c36c376e2e6`; workflow pins use those immutable commits. Provenance is `.github/action-pins.json`. Standard `ubuntu-24.04` / `windows-2025` labels remain platform-managed images; requested Temurin `17.0.18+8` differs from the locally tested Microsoft JDK vendor. CI prepares the cache online, then asserts offline core plus mechanical/unresolved fixtures and packages review bytes. No provider secret, upload/release/deploy or paid-call path is configured. Free standard public-repository runner scope and billing-source links are in `docs/CI.md`; no future private-account billing state is assumed.

Unexecuted in M3: hosted CI; POSIX wrapper; Linux process-inventory/demo branch; a separate Windows PowerShell 5.1 run; the new script's online-preparation and `-Extended` execution; separate packaged-CLI execution; live models; arbitrary application coverage; supported contemporary targets; peak RSS/network measurements; OS force-kill report guarantees. Existing M1/M2 preparation and mechanical/unresolved/interruption/final-failure execution remains explicitly historical below. Cooperative in-process mock authority, trusted executable checkouts, per-file atomic replacement/rollback and no global filesystem isolation remain limitations. Worker stops after delivery and requests main lease release and independent whole-repository acceptance.

---

## Archived assigned M2 evidence — recorded 2026-10-07

The following record preserves its original status and pending-review statements as of 2026-10-07. Main's later M2 acceptance and final M3 status above supersede those historical statements. The separately retained `outputs/EVIDENCE-M2.md` is unchanged.

Date: 2026-10-07. Repository: `C:/Users/shash/Documents/CodexPortfolios/agentic-ai-2026-10-07/spring-migration-agent`. Existing Java Microsoft 17.0.18, Maven 3.9.11, Git 2.45.2.windows.1. Historical unsupported Boot 2.7.18 to 3.0.13; fixture-only, trusted checkout. No model credentials, live calls, account/production changes, deployment or publication.

Status: **assigned M2 delivered for main independent review; worker stopped before M3**. M1 was independently accepted by main; its historical evidence remains in `outputs/EVIDENCE-M1.md`. M2 used the named `spring-migration-agent` lease, one Maven/application-heavy child at a time. All owned JVMs are stopped; release requested from main. Main-owned review/resource/acceptance files were read only. No additional chats or subagents.

## Executed preparation and unit checks

All commands execute from the repository root above. No dependency pins changed. Disk check before work: C: approximately 17.53 GiB free. Existing scoped cache remains 399 JARs / 277,657,216 JAR bytes; `scripts/audit-dependencies.ps1` exited 0 with no unresolved declared/inherited licenses and no source-available recipe module. This is an upstream declaration audit, not binary equivalence or vulnerability verification.

* `.\mvnw.cmd -o clean verify`, then `.\mvnw.cmd -o verify`: exit 0, first 16 tests, then 17 after protected-verifier tampering coverage. Final `.\mvnw.cmd -o clean verify` exited 0 with **18 tests, zero failures/errors/skips**, including forward-only parent rejection; explicit test-fork heap cap is in the final build. Final build argv/exit/JAR SHA are in `outputs/m2-receipts/bounded-harness-build.json`; full log and preserved Surefire XML/text are in `outputs/m2-receipts/bounded-harness-build.log` and `outputs/m2-unit-results/`. Compiler/JAR/shade output is real. Benign overlapping module-info/MANIFEST warnings remain.
* `.\scripts\run-m2-evidence.ps1 -Phase prepare`: exit 0. Expanded command/exit/timestamps are in `outputs/m2-receipts/prepare-cache.json`; online Maven baseline, real OpenRewrite, target verification and repair/final verification all ran. Three original tests per successful stage, final HTTP 6/6. `outputs/m2-prepare-cache/cache-preparation.json` explicitly requires subsequent offline proof.
* First three-mode comparison, `java -Xmx2g -jar target/spring-migration-agent.jar compare --agent mock --offline --out outputs/m2-three-mode-offline`: exit 0. Identical snapshot `51e7259f1a78ffc82f632bbe85ea6e4c66775ba9e2dfa34881963d68c5355107`; recipes-only exit 2, agent-only exit 0 with two proposals, hybrid exit 0 with one proposal. Every Maven command used actual `-o`. This preliminary run preceded explicit Surefire fork heap/forward-only policy tightening; it is retained and will be superseded by the final matrix.

Fast controller tests use simulated diagnostics, explicitly distinct from real Maven/HTTP integration. Process tests start real controlled Java children/grandchildren: timeout and thread interruption assert all recorded PIDs dead; a 2 MiB output child proves 1 MiB retained-log cap. Java argv are passed as vectors, including a space-bearing working directory. Authority tests reject protected/escaping/case/device/stream paths, stale second edits, malformed/duplicate proposals, POM/plugin/dependency smuggling, unsupported properties, oversized reads/proposals and no-op writes. Injected second-file commit failure restores every original byte and deletes staging files. Controller tests stop on no-op/repeated hashes/proposal limits and reject late proposals after deadline. Protected test tampering is detected before a build. Transcript tests reject changed source/request/contract hashes, tampered hashes and unused recordings.

## Final integration receipts

All final phases executed successfully under their expected-outcome assertions. Exact expanded outer command vectors, cwd, actual/expected exit, timestamps and log paths are in `outputs/m2-receipts/*.json`; each `report.json` contains the actual child argv, command exit/timeout/cleanup and Maven/HTTP observations. These are worker-run checks; main independent M2 acceptance remains pending.

Commands actually executed:

```powershell
.\mvnw.cmd -o clean verify
.\scripts\run-m2-evidence.ps1 -Phase prepare
.\scripts\run-m2-evidence.ps1 -Phase compare
.\scripts\run-m2-evidence.ps1 -Phase remaining
# remaining sequentially ran replay, fixtures, failures and scripts/run-m2-probes.ps1.
.\scripts\check-m2-evidence.ps1
.\scripts\audit-dependencies.ps1
```

`prepare` preceded the final policy/test-fork tightening and succeeded against the same pins/dependency graph. The final `compare` and all `remaining` runs used the final 18-test harness. Comparison, remaining, probes, artifact check and audit shell commands all exited 0 because they assert the individually reported expected success/failure exits. Direct underlying Java commands and their individual nonzero exits are preserved, not replaced with fabricated success. Scripts use fixed evidence paths and require new directories; on this populated checkout, reproduce the CLI commands with new `--out` names.

| Executed case / output under `outputs/` | Actual Java/engine outcome | Accepted proposals | Observed verification |
| --- | --- | --- | --- |
| `m2-final-three-mode-offline/recipes-only` | exit 2, `verification_failed` | 0 | Original tests 3 per stage; baseline 6/6, post/final 5/6 HTTP |
| `m2-final-three-mode-offline/agent-only` | exit 0, `verified`; no recipe | 2 | Target start compilation fails; mechanical repair 5/6, route repair/final 6/6; preserved tests 3 on successful builds |
| `m2-final-three-mode-offline/hybrid` | exit 0, `verified` | 1 | Recipe 5/6, repaired/final 6/6; preserved tests 3 per build |
| `m2 replay offline` | exit 0, `recorded_mock_replay` | 1 | Real recipe/build/HTTP through a space-bearing output path; final candidate equals fresh mock hash |
| `m2-mechanical-offline/{recipes-only,agent-only,hybrid}` | all exit 0, `verified` | 0 / 1 / 0 | All final checks 6/6; no route repair needed |
| `m2-unresolved-offline` | exit 2, `verification_failed`; `unsupported_diagnostics` | 0 | Baseline 7/7, post/final 5/7; protected POST slash check stays failed |
| `m2-missing-cache` | exit 4, `offline_cache_miss` | 0 | Actual Maven `-o` rejects uncached `org.springframework.boot:spring-boot-starter-parent:pom:2.7.18`; no online retry |
| `m2-run-deadline` | exit 5, `budget_exhausted` | 0 | One-second run budget exhausted before Maven began; report/patch available and completed Git children cleaned |
| `m2-command-timeout` | exit 4, `command_timeout` | 0 | Actual baseline Maven one-second timeout, cleanup complete |
| `m2-proposal-budget` | exit 5, `budget_exhausted` | 1 | Mechanical edit accepted; another clean final build executes 3 tests and still fails slash check 5/6 |
| `m2-action-budget` | exit 5, `tool_budget_exhausted` error | 0 | One consumed read; next tool denied, count remains 1 |
| `m2-replay-mismatch` | exit 6, `policy_denied`, `replay_request_source_mismatch` | 0 | Real baseline/recipe/post verification of behavior-preserving source-comment change; replay rejected before editing |
| `m2-probe-final-failure` | probe process exit 0; engine exit 2 | 1 | Host test seam inserts invalid Java after passed repair verification; fresh final compilation fails, 0 tests, distinct workspace |
| `m2-probe-interrupt` | probe process exit 0; engine exit 8, `interrupted` | 0 | Actual baseline Maven child interrupted; report written, cleanup complete |

Main matrix snapshot: `51e7259f1a78ffc82f632bbe85ea6e4c66775ba9e2dfa34881963d68c5355107`. Mechanical matrix snapshot: `6855b4ec592b3e833ee3772fd3735de11191e2caa87b5d54f0ee866e2acd0194`. Each comparison's three modes start from one captured immutable snapshot, and both comparisons assert identical digests. `comparison.json` is descriptive mock harness evidence, not strategy/model superiority.

`scripts/check-m2-evidence.ps1` exited 0: **16 reports, 52 actual Maven `-o` invocations**. It recomputes candidate digests, checks original/baseline/protected file hashes against actual bytes, requires explicit `-Xmx2g` and verification `-DargLine=-Xmx2g`, verifies preserved test counts, distinct verification workspaces, final-stage success for verified reports, matching replay candidate, expected matrices, and command/app cleanup. `outputs/m2-evidence-summary.json` records all assertions and **0 owned JVMs**, with C: **16.46 GiB free** at observation. No peak RSS/network traffic measurement is implied.

Final patch check: fresh `outputs/m2-patch-review`, local `git init --quiet`, `git config core.autocrlf false`, `git apply --check` and `git apply` both exited 0. All six copied source/POM/config/test file hashes match final hybrid candidate bytes. Exact argv and hashes are in `outputs/m2-patch-apply-evidence.json`. A separate M2 rebuild of this byte-identical applied tree was not repeated; final hybrid verification already built those bytes. M1 separately executed its applied-patch build/HTTP checks.

Final dependency audit at `2026-10-07T16:58:19Z` still reports 399 JARs / 277,657,216 JAR bytes, zero unresolved declarations, zero source-available recipe artifacts. No new dependency download/pin change was needed. `-DargLine=-Xmx2g` explicitly caps verification test forks as well as direct `-Xmx2g` Maven/app children. Every ordinary final verification materializes another clean workspace with original tests and host-approved POM; exhausted infrastructure/policy/interruption/deadline paths stop with available evidence.

## Limits and unexecuted work

Scripted mock and `recorded_mock_replay` are harness exercises, not LLM or migration-performance benchmarks; zero live model calls. Replay binds exact canonical request/source hashes and omits unspecified timestamp-bearing HTTP error bodies while full responses remain in reports. Recordings are not authenticated; replay actions still undergo gateway validation.

Multi-file updates validate all edits before staging and roll back commit failure; they do not provide filesystem-wide isolation against concurrent readers or hostile Java. Deadlines bound host tools/HTTP and are checked around the trusted cooperative in-process mock; arbitrary blocking agent implementations cannot be preempted. Cleanup/evidence have a short bounded allowance after deadline. OS force-kill reporting is not guaranteed; the tested interruption is Java thread interruption. Peak RSS and network packet traffic are not measured; actual Maven `-o` and no online fallback are asserted rather than a claim of network isolation.

POSIX/Linux wrapper execution, external CI, release packaging, arbitrary Spring application coverage, contemporary supported targets, broader migrations and any live model adapter remain unexecuted/unassigned. M3 requires a new main assignment. Main independent M2 review is pending. Patch application on this HP must use a fresh Git repository with `git config core.autocrlf false` for byte preservation.
