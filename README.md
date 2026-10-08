# Spring Migration Agent

[![CI](https://github.com/sha-shank-03/spring-migration-agent/actions/workflows/verify.yml/badge.svg)](https://github.com/sha-shank-03/spring-migration-agent/actions/workflows/verify.yml)

Open-source Apache-2.0 project. [Project context](docs/PROJECT_CONTEXT.md) · [Independent local verification](docs/INDEPENDENT_VERIFICATION.md) · [Contributing](CONTRIBUTING.md)

A Java 17 harness that runs real OpenRewrite recipes, permits bounded scripted repairs, and verifies behavior outside the editing agent's authority.

**The only target is historical Spring Boot 2.7.18 → 3.0.13 for the included MVC/validation fixtures. Both versions are unsupported deployment targets.** This is a reproducible migration exercise, not a production upgrade recommendation. Agent execution is `scripted_mock` or `recorded_mock_replay`; there is no live model adapter, account, API key or paid call.

## Windows quickstart

Requirements: existing Java 17, Git, and PowerShell 7 on Windows. Run from this repository root. The first build/preparation downloads pinned artifacts from Maven Central into repository `work/`; Maven need not be installed globally. Windows PowerShell 5.1 was not separately validated.

```powershell
.\mvnw.cmd -B clean verify
if ($LASTEXITCODE -ne 0) { throw 'Build failed' }
.\scripts\demo.ps1 -Out outputs\my-review
Get-Content outputs\my-review\summary.json
```

The asserting demo prepares the cache online, runs an identical-snapshot three-mode comparison offline, replays the hybrid recording in a path with spaces, checks empty-cache/timeout/changed-source replay failures, applies the final patch in a fresh fixture, and checks hashes, actual Surefire XML/HTTP results and child cleanup. To include the mechanical/unresolved fixtures, add `-Extended`. The output path must be new and under repository `outputs/`; existing evidence is preserved.

After cache preparation, this uses only Maven offline invocation paths:

```powershell
.\mvnw.cmd -o clean verify
if ($LASTEXITCODE -ne 0) { throw 'Offline build failed' }
.\scripts\demo.ps1 -Offline -Out outputs\another-review
```

`-Offline` fails on an incomplete cache and never retries online. Actual Maven `-o` is checked on recipe and verification argv; this is not a claim of OS/network isolation. Every child tool and verification test JVM is capped at 2 GiB; builds/apps are sequential.

## POSIX quickstart

Requirements: Java 17, Git and a POSIX shell. The wrapper is invoked through `sh`, so an executable Git mode bit is unnecessary. These commands are prepared; POSIX execution is not part of the Windows evidence.

```sh
set -eu
sh ./mvnw -B clean verify
java -Xmx2g -jar target/spring-migration-agent.jar prepare-cache --out outputs/my-cache
java -Xmx2g -jar target/spring-migration-agent.jar compare --agent mock --offline --out outputs/my-comparison
cat outputs/my-comparison/comparison.json
java -Xmx2g -jar target/spring-migration-agent.jar demo --mode hybrid --agent replay \
  --replay outputs/my-comparison/hybrid/mock-transcript.json --offline --out outputs/my-replay
```

With PowerShell 7 already available, `pwsh -File ./scripts/demo.ps1 -Out outputs/my-review` runs the same asserting host script. Prepared Windows/Linux CI uses PowerShell 7 already present on standard runners. No PowerShell installation or hosted CI run occurred in this local task.

## Modes and local checkout

| Fixture | Recipes-only exit/proposals | Agent-only exit/proposals | Hybrid exit/proposals |
| --- | --- | --- | --- |
| `mvc-validation` | 2 / 0 | 0 / 2 | 0 / 1 |
| `mechanical-only` | 0 / 0 | 0 / 1 | 0 / 0 |
| `unresolved-repair` | Expected final failure; this mock cannot repair the protected POST trailing-slash check | Same limited mock policy | 2 / 0 observed in M2 |

These are scoped scripted-harness outcomes, not an LLM benchmark. `compare` asserts the expected matrix and snapshot equality, while individual failing exits remain in `comparison.json` and reports. Agent-only executes no recipe; the mock proposes exact parent/namespace/header edits, then the supported route repair. Hybrid runs actual core recipes before the same route policy. The mechanical fixture already maps both routes. The unresolved case retains an unsupported failed check and declines repair.

For one mode, read and assert its actual exit:

```powershell
java -Xmx2g -jar target\spring-migration-agent.jar demo --mode recipes-only --offline --out outputs\recipes-review
if ($LASTEXITCODE -ne 2) { throw 'Expected protected slash regression was not observed' }
java -Xmx2g -jar target\spring-migration-agent.jar demo --mode hybrid --offline --out outputs\hybrid-review
if ($LASTEXITCODE -ne 0) { throw 'Hybrid final verification failed' }
```

`migrate --input PATH --contract PATH --target boot-2.7.18-to-3.0.13 --mode hybrid --agent mock --offline --out NEW_PATH` works only for a trusted local fixture-compatible copy: exact Boot parent, Java 17, supported starters/build layout and artifact `mvc-validation:1.0.0`. General company applications are outside verified coverage. The external contract must already exist; the caller's checkout remains untouched. Unsupported input/preflight exits 3. CLI output paths must be new and outside input/contract trees.

Cache and wrapper homes are scoped to `work/m2` and `work/maven-home`; settings select Maven Central without global user credentials. Maven 3.9.11/wrapper 3.3.4 hashes and all Java dependency pins are explicit. `--cache` can select another directory inside repository `work/`. Cache preparation proves these fixture paths, not arbitrary projects.

## Review artifacts and limits

Each run retains `report.json`/`report.md`, source/contract/protected hashes, actual command argv/exit/timeout/cleanup, original test counts, HTTP observations, bounded logs, fresh verification trees and patches. `migration.patch` is baseline to current candidate; `recipe.patch` covers recipes and is empty for agent-only; `agent.patch` covers repairs, or the full agent-only result. Fresh mock requests are recorded in `mock-transcript.json`. Early failure paths retain the evidence available at the stopping point. Forceful OS termination cannot guarantee a report.

Replay requires matching canonical request and exact source hashes. Full HTTP bodies remain in reports; unspecified timestamp-bearing error bodies are excluded from replay requests. Recordings are not authenticated. Every replay edit still passes gateway validation. See [contracts](docs/CONTRACTS.md) for fields, digest encoding, labels and exit codes.

The gateway permits existing main Java source, one forward header-key rename and the exact forward Boot parent edit. It rejects tests/contracts/config/plugins/dependency additions, stale preimages, malformed/oversized edits, paths escaping or bypassing authority, and no-ops. Multi-file proposals validate fully before staging and roll back commit failures. Fresh verification uses preserved original tests/POM/contract outside candidate authority and rebuilds after the last edit. Limits are three attempts, twenty actions, 180 seconds per command, 900 seconds per run, 64 KiB reads, 256 KiB aggregate changed-file contents per proposal and 1 MiB retained logs; flags can reduce budgets.

This is a cooperative trusted mock over trusted executable source, not an OS sandbox or arbitrary-agent preemption mechanism. Per-file atomic replacement/rollback is not global isolation. Fixture success does not certify broader compatibility. [Architecture](docs/ARCHITECTURE.md) explains the trust boundaries and acknowledges existing OpenRewrite, Spring Boot Migrator and agent-harness work.

Patch application must use a fresh fixture Git root and `core.autocrlf=false` to preserve bytes on Windows:

```powershell
New-Item -ItemType Directory outputs\patch-review | Out-Null
Copy-Item examples\mvc-validation\pom.xml outputs\patch-review
Copy-Item examples\mvc-validation\src outputs\patch-review -Recurse
git -C outputs\patch-review init --quiet
git -C outputs\patch-review config core.autocrlf false
git -C outputs\patch-review apply --check (Resolve-Path outputs\hybrid-review\migration.patch)
if ($LASTEXITCODE -ne 0) { throw 'Patch check failed' }
git -C outputs\patch-review apply (Resolve-Path outputs\hybrid-review\migration.patch)
if ($LASTEXITCODE -ne 0) { throw 'Patch application failed' }
```

## Final review package and evidence

After a successful build, `scripts/package-review.ps1 -Out outputs/my-package` creates a local source/CLI/notice ZIP and verifies every ZIP entry against its SHA-256 manifest. It includes the CLI under `bin/`, source, wrapper, fixtures, scripts, docs, license notices and the dependency catalog. Maven caches/distributions, Git data and generated Spring application JARs are excluded. No release, push, deployment or hosted publication is performed. Keep the accompanying notices when distributing the CLI. The source wrapper can bootstrap the excluded Maven tool; `bin/` alone does not include that tool/cache.

`EVIDENCE.md` distinguishes historical M1/M2 worker results, main independent acceptance, final M3 local checks and unexecuted hosted-CI/POSIX coverage. The final workflow is prepared with immutable action pins, read-only checkout, serial standard Windows/Linux Java 17 jobs and asserting offline mock/replay/failure paths; see [CI](docs/CI.md). This task remains local and stops for main whole-repository acceptance.

Project code is Apache-2.0. The packaged CLI contains Gson/Error Prone Apache components; external tools/tests/fixtures have their own declared licenses, including non-Apache cases. Read [DEPENDENCIES.md](DEPENDENCIES.md), [source attribution](docs/SOURCES.md), `LICENSE`, `NOTICE` and `third-party/` before repackaging excluded upstream binaries. The retained catalog covers all 399 actually tested Windows cache JARs and does not relabel them all as Apache.

## Get the source

```sh
git clone https://github.com/sha-shank-03/spring-migration-agent.git
cd spring-migration-agent
```

Follow the quickstart above for runtime/dependency setup. Java projects download pinned build dependencies once; cached mock demos can then run offline.
