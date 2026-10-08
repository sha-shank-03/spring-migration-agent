# Spring Migration Agent — proposed implementation plan

Planning date: **2026-10-07**. Current status on **2026-10-08**: **M1 and M2 independently accepted; final assigned M3 delivered for main whole-repository acceptance**. Final M3 adds quickstarts, architecture/contracts, attribution, reusable asserting demo/review packaging and prepared immutable-pinned Windows/Linux Java 17 CI. Runtime, fixtures, recipes, contracts, POM and wrappers are unchanged from accepted M2. One authorized offline clean build passed 18 tests; the fresh core offline demo passed seven cases / 24 Maven invocations. Hosted CI and POSIX execution remain untested. The numbered proposal and research read log below retain their original planning-time context. Current executed evidence is maintained separately in `EVIDENCE.md`, with original M1/M2 records under `outputs/`.

Repository: `C:/Users/shash/Documents/CodexPortfolios/agentic-ai-2026-10-07/spring-migration-agent`.

## 1. Problem and user journey

A backend team can automate dependency and import changes and still ship a behavior regression. It needs a migration candidate that is easy to review, a bounded mechanism to repair diagnosed failures, and verification whose inputs cannot be rewritten by the editing agent.

Build a Java 17 CLI for exactly one migration: **Spring Boot 2.7.18 to 3.0.13**, for a single-module Maven Spring MVC application using the Boot parent and Boot-managed web/validation starters. This is a historical, reproducible migration laboratory and a small developer harness, not a recommendation to deploy these unsupported historical versions. Java is already installed and suits the user's backend experience. No Python runtime, application server framework for the harness, database, queue, Docker, hosted service, or paid model is needed.

Proposed entry point after implementation:

```powershell
.\mvnw.cmd -B verify
java -jar target/spring-migration-agent.jar demo --mode hybrid --agent mock --out outputs/demo
java -jar target/spring-migration-agent.jar migrate --input C:/trusted-service --target boot-2.7.18-to-3.0.13 --mode hybrid --agent mock --contract C:/contracts/service.json --out outputs/service
java -jar target/spring-migration-agent.jar compare --fixture mvc-validation --agent mock --out outputs/comparison
```

The CLI snapshots the input, checks baseline behavior, creates an isolated candidate copy, runs recipes where requested, verifies the candidate, permits up to three repair proposals, and emits a final patch plus evidence. It never applies changes to the caller's checkout. Input may contain uncommitted source changes: snapshot and hash the actual files, rather than silently using HEAD. Refuse unsupported build layouts before editing. Output paths must be new and outside the input; refuse collisions and nested output paths.

Artifacts: `migration.patch`, `recipe.patch`, `agent.patch`, `report.json`, `report.md`, `events.jsonl`, and bounded command logs. Reports distinguish baseline, recipe-stage, repair-stage, and final verification. A failed migration still produces useful diagnostics and any reviewable candidate patch.

## 2. Existing tools and the contribution

* [OpenRewrite's Maven plugin](https://docs.openrewrite.org/reference/rewrite-maven-plugin) already runs recipes and produces dry-run patches. Do not reimplement its transformation engine.
* [Spring Boot Migrator](https://github.com/spring-projects-experimental/spring-boot-migrator) already offers Spring migrations using OpenRewrite and a CLI. This project is not the first Spring migration tool.
* [SWE-agent](https://github.com/SWE-agent/SWE-agent) and its linked successor already demonstrate general editing-agent harnesses. We will not claim novel tool calling or general agent repair.

The contribution is a small, inspectable Java migration experiment: identical starting snapshots for recipes-only, agent-only, and hybrid modes; editing capabilities narrower than verification authority; explicit stopping reasons; and evidence tying each accepted edit to verification results. Its demo uses a documented framework behavior change rather than claiming that compilation proves compatibility.

Comparisons are **mock harness evaluations**, with recipe-pack scope and mock knowledge documented. They are not evidence that an LLM or hybrid strategy outperforms OpenRewrite. Report observed pass/fail, proposal count, accepted/rejected changes, files changed, and observed duration; do not invent token use or monetary costs.

## 3. Narrow MVP and non-goals

### Supported path and fixture

Target ID `boot-2.7.18-to-3.0.13`, schema/version `springmig.target/v1`. Preconditions: JDK 17, Boot parent exactly 2.7.18 with empty relativePath, Java source level 17, one module, no custom parent/BOM overrides, web/validation starters, ordinary `application.properties`, and trusted source code. Reject Gradle, multi-module builds, symlinks/reparse points, non-regular source entries, oversized input, unsupported dependency families, and an already migrated or mismatched version.

The fixture has a greeting MVC endpoint and validation annotations imported from `javax.validation`. Its baseline contract includes `/greeting` and `/greeting/`, expected response content, invalid-input behavior, and an unknown route returning 404. Spring's migration guide documents Jakarta package changes and a trailing-slash matching default change in Framework 6. The latter gives a mechanical migration a real behavior failure to diagnose. The mock agent's supported repair adds an explicit additional handler route, without globally enabling permissive matching.

Use a **project-owned scoped OpenRewrite recipe pack** composed from Apache-licensed core recipes:

1. `org.openrewrite.java.ChangePackage`, `javax.validation` to `jakarta.validation`, recursive; include `javax.servlet` to `jakarta.servlet` only for supported servlet source present in the fixture.
2. `org.openrewrite.properties.ChangePropertyKey`, exact key `server.max-http-header-size` to `server.max-http-request-header-size`.
3. `org.openrewrite.maven.UpgradeParentVersion`, Boot parent to exact `3.0.13`, rather than a floating `3.0.x` selector.

Recipe pack ID `springmig.recipes.MvcValidationBoot3_0_13`, pack revision `1`, declarative format `specs.openrewrite.org/v1beta/recipe`. Its recipe file and SHA-256 are recorded in each run. It intentionally covers this declared surface, not the full upstream Spring composite recipe. First real integration must confirm attribution, application order, and plugin discoverability; source/artifact checks alone do not establish successful execution.

### Modes and agent implementation

* `recipes-only`: real OpenRewrite execution and independent verification; no repair proposals.
* `agent-only`: no OpenRewrite execution; the scripted mock agent uses the target goal and diagnostics to propose the same narrow mechanical edits and supported route repair.
* `hybrid`: real recipes, verification, then the same bounded mock agent policy on remaining failures.

The mock participates through an `EditingAgent` Java interface and host tools, not direct file writes. Tool actions: list permitted files, read bounded source, propose replacement of an exact text block with a preimage hash, request verification, or finish. Local protocol/event version `springmig.agent/v1`; it is a project contract, not MCP or another claimed standard. A captured mock transcript may be replayed only when request/source hashes match. Label it `recorded_mock_replay`; label fresh mock execution `scripted_mock`. No live model adapter is part of this phase.

Non-goals: arbitrary Spring/Security/JPA/Cloud migration, migrating the JDK itself, upgrading to today's production release, editing tests to make them pass, network agent tools, shell access for the editing agent, API-provider integration, hosted UI, production deployment, GitHub publication, security certification, large-scale benchmarking, or a general process sandbox.

## 4. Architecture and trust boundaries

Flow: `CLI -> preflight/snapshot -> baseline verifier -> mode runner -> OpenRewrite -> verifier -> bounded repair controller -> final clean verifier -> patch/evidence writer`.

Single Maven-built Java CLI. Packages/components remain small: input validation, workspace snapshotting, process runner, recipe executor, tool gateway, mock/replay agent, repair controller, verifier, and evidence writer. Gson is the one application dependency for structured reports and strict protocol parsing; JUnit is test-only. OpenRewrite is a subprocess tool dependency, not another application service.

**Editing authority:** only candidate `src/main/java`, selected `src/main/resources/application.properties`, and the exact parent-version element of `pom.xml`. Deny all test paths, verifier resources, contract files, Maven flags/configuration, plugin definitions, dependency additions, `.git`, wrapper files, report files, absolute/parent-relative paths, Windows alternate streams/device names, and case-variant bypasses. Treat a proposed POM change semantically: only the supported parent version may differ. Edits are atomic after checking all files and preimages; a partial multi-file proposal must not leak through.

**Verification authority:** host-owned test/contract snapshot outside the candidate, hashed before and after every attempt. Compile and run from a fresh verification tree using the original approved POM transformed only by host-owned target settings; rebuild from candidate main sources/resources and immutable original tests. The verifier never trusts an agent-produced test report, skip setting, or test modification. The fixture additionally uses host-run HTTP assertions against a child app bound to loopback with a fresh port. For a local checkout, execute its preserved tests and an optional user-supplied external HTTP contract; if no behavior contract exists, report `behavior_verification=not_provided`, not full compatibility.

The final verifier rematerializes sources and runs cleanly after the last edit; cached success from an earlier candidate is insufficient. Verify exact target parent, no forbidden changes, successful compilation/tests, a nonzero expected test count when tests are declared, contract outcomes, and unchanged protected hashes. Main's acceptance suite remains outside this editing agent and main may add independent cases without exposing editing permissions.

**Budgets:** at most 3 repair proposals, 20 tool actions total, 256 KiB per proposed patch, 64 KiB per source read, 1 MiB captured log per command, 180 seconds per build/recipe command, and 15 minutes per run. Reject/stop on malformed action, forbidden edit, stale preimage, repeated candidate hash, exhausted budget, or unavailable required tool. Infrastructure failures (dependency cache misses, process timeout) do not masquerade as source repair opportunities. Kill child processes on timeout and report cleanup results. One run holds at most one Maven-heavy subprocess at a time; proposed `-Xmx2g` for recipe JVM, not Maven parallel builds. These limits are enforcement targets to test, not present guarantees.

Use repository-scoped Maven home/cache under `work/`, explicit settings with only Maven Central, and pinned binaries. Do not read credentials from the user's global Maven settings. Use argument vectors; never concatenate agent content into shell commands. Windows batch invocation must use a small reviewed launcher or the bootstrapped Maven Java launcher, tested with paths containing spaces. Builds execute trusted checkout code on the host: copied directories and tool allowlists are not OS isolation against malicious Java/build plugins or a native malicious agent.

## 5. Pins, availability, and attribution

All retrievals below were performed **2026-10-07 UTC**. The proposed pins are available without authentication. Detailed POM GET and artifact HEAD records are in `work/research/artifact-availability.json`; inherited Maven license chains are in `work/research/inherited-licenses.json`. Every recorded request succeeded with HTTP 200. No artifact JAR/archive or transitive build graph has been downloaded/resolved yet.

| Component | Pin | License evidence | Purpose |
| --- | --- | --- | --- |
| Java | 17; local Microsoft OpenJDK 17.0.18 | Existing machine runtime; not redistributed | Harness and fixture |
| Apache Maven | 3.9.11 | Apache-2.0; tagged source LICENSE and inherited POM | Scoped build distribution |
| Maven Wrapper distribution | 3.3.4 | Apache-2.0; tagged source LICENSE and inherited POM | Windows/POSIX quickstart |
| Rewrite Maven plugin | 5.46.0 | Apache-2.0; published POM and tagged LICENSE | Real transformation tool |
| Rewrite core/Java/Java17/Maven/Properties | 8.41.0, plugin's pinned BOM version | Apache-2.0; POMs and tagged core LICENSE | Building-block recipes/type parser |
| Spring Boot parent/web/validation/plugin | 2.7.18 baseline, 3.0.13 target | Apache-2.0; published POMs and tagged LICENSE | Fixture/application |
| Gson | 2.13.2 | Apache-2.0; POM and tagged LICENSE | JSON reports/protocol |
| JUnit Jupiter | 5.11.4 | EPL-2.0; POM and tagged LICENSE | Harness tests |
| Maven compiler/surefire/jar/shade/dependency plugins | 3.14.1 / 3.5.4 / 3.4.2 / 3.6.1 / 3.8.1 | Apache-2.0 through verified parent chains | Build, package, cache preparation |

OpenRewrite currently documents plugin 6.49.0 and rewrite-spring 6.40.0, with newer distribution through authenticated Code Genome downloads. Its current Spring module is source-available. Maven Central metadata read separately reports last public plugin 6.46.1 and Spring module 6.37.1; these are different inventories. The chosen older plugin uses verified Apache building blocks. We will not put the current source-available `rewrite-spring` into the tool or describe it as Apache OSS. Historical Spring module 5.22.0 has Apache evidence, but its broad transitive recipe graph and floating migration selectors are unnecessary for this limited pack.

Project license: Apache-2.0. Deliver `LICENSE`, `NOTICE`, and `DEPENDENCIES.md` separating harness, fixture, and build/tool dependencies. During leased bootstrap record the effective dependency/plugin graph, resolved coordinates and hashes, retain required dependency notices, and check transitive license exceptions. A direct POM license audit is not a completed transitive licensing audit. Maven ZIP's retrieved SHA-512 is `03e2d65d4483a3396980629f260e25cac0d8b6f7f2791e4dc20bc83f9514db8d0f05b0479e699a5f34679250c49c8e52e961262ded468a20de0be254d8207076`; recheck on actual download and configure wrapper distribution integrity.

## 6. Bounded milestones

**M1 — first real end-to-end slice.** Main reviews/assigns this milestone and grants a named heavy-process lease before wrapper/bootstrap/dependency downloads or integration builds. Add wrapper, Java CLI and one fixture; preflight, input snapshot, real exact-version recipes, host-owned verification, deterministic route-repair mock, and patch/report output. Demonstrate recipes-only versus hybrid on the same fixture. Exit only after a real baseline, real recipe invocation, actual post-recipe behavior failure, and actual repaired final verification. If core recipe loading/attribution fails, record the exact error and return a bounded dependency correction to main; do not substitute text replacement while reporting OpenRewrite execution.

**M2 — authority/failure tests and three-mode comparison.** Add agent-only, compare from identical snapshots, replay of captured mock requests, stopping rules, timeouts, malformed actions, forbidden/stale edits, integrity checks, interrupted run reporting, and offline cache behavior. Add a mechanical-only fixture and an unresolved repair scenario. Assert original checkout/protected files remain unchanged and failure reports are complete. Test large/escaping paths and Windows argument handling. Keep metrics descriptive.

**M3 — practical delivery.** README Windows-first and POSIX quickstarts, cache preparation and offline commands, useful local checkout example, architecture/agent contract/evaluation docs, license attribution, pinned CI and evidence report. CI runs unit tests plus actual fixture migration with mock agent; no secrets or paid calls. Use Windows and Linux jobs serially per workflow concurrency policy; GitHub CI execution is prepared but unexecuted while this phase remains local. Main independently executes quickstart and protected acceptance cases, then sends concrete defects only.

There is no automatic implementation continuation after plan review. Each implementation assignment must come from main. Resource registry is read-only; only main grants/releases leases. Estimate for scheduling only: a few hundred MiB of Maven cache, potentially more due to parser/native tool dependencies, and one bounded JVM build at a time; measure actual disk/memory before claiming values.

## 7. Tests and acceptance commands

Commands below are **proposed acceptance**, not executed evidence. Run from the repository root after an implementation assignment and required lease.

```powershell
.\mvnw.cmd -B verify
java -jar target/spring-migration-agent.jar doctor
java -jar target/spring-migration-agent.jar prepare-cache --fixture mvc-validation
java -jar target/spring-migration-agent.jar compare --fixture mvc-validation --agent mock --offline --out outputs/comparison
java -jar target/spring-migration-agent.jar demo --mode hybrid --agent mock --offline --out outputs/demo
git apply --check outputs/demo/migration.patch
java -jar target/spring-migration-agent.jar verify-report --report outputs/demo/report.json
```

`git apply --check` must run in a fresh copy of the baseline fixture, not the harness repository. The README command will spell out this directory transition. The verification report checker only checks consistency/hashes; it does not independently rerun Maven or certify application correctness. Main's acceptance reruns the documented demo and verifier itself.

Concrete assertions:

* Unit suite exits 0; denied edits cannot mutate any file, including partial proposals. A source-hash mismatch and patch traversal produce the right explicit failure status.
* Doctor reports actual JDK, pinned Maven availability, known target and cache readiness. Missing prerequisites never produce a successful migration report.
* Fixture baseline compiles and passes preserved tests plus HTTP contract.
* Recipes-only changes parent to 3.0.13, imports to Jakarta, and the selected property; it compiles but fails the trailing-slash behavior contract. Expected exit is 2 (`verification_failed`), while comparison completes with an explicit results matrix.
* Hybrid and agent-only fresh scripted mocks pass all target and behavior checks within budgets. Reports identify the exact recipe pack, mode, mock source, candidate hashes, observed commands/results, and protected contract hashes. Agent-only records `recipe_executed=false`.
* Accepted route repair preserves ordinary response/validation behavior and unknown-route 404; it cannot pass by returning 200 for every route. Contract fixture tests include this counterexample.
* Three comparison runs start from identical snapshot hashes. Differences in patch/edit count are measured, never fabricated expectations of superiority.
* A no-op/repeating mock stops without unbounded retries; malformed/replay-mismatched responses and unsupported repair produce nonzero exit and inspectable evidence. Failure categories include `unsupported_input` (3), `tool_unavailable` (4), `budget_exhausted` (5), `policy_denied` (6), and `baseline_failed` (7), finalized in the CLI contract.
* Timeout tests use a short-lived controlled child process and prove termination; real integration tests assert no leftover service child and no stale verification output reused after a subsequent failed build.
* Missing cache under `--offline` produces `tool_unavailable` with the missing coordinate; it cannot silently switch online. After `prepare-cache`, actual Maven `-o` demo/compare must work. First-time cache preparation requires network access and a lease; "offline mock" is not a promise of zero initial artifact downloads.
* Applying the final patch to a pristine baseline succeeds, produces the reported target tree, and independently passes verification. Original input files and external acceptance contract remain byte-identical.

Fast unit cases mock subprocess/tool boundaries and are labelled as such. Integration cases run actual Maven, OpenRewrite and Spring; store complete command exit codes and link logs. Add neither mock compilation success nor fabricated HTTP observations to the real verification report.

## 8. Material risks and remaining decisions

1. **Historical target relevance:** the explicit narrow target demonstrates the harness and a common 2-to-3 migration boundary; extending to supported contemporary releases is separate work. Document unsupported versions prominently. Main may prefer a different exact target before M1, but this does not block the present plan.
2. **Artifact/runtime compatibility:** artifact availability and Apache declarations are verified, but plugin/core/JDK/fixture execution and full transitive license audit are unexecuted. M1 is the mandatory integration gate.
3. **Recipe scope:** generic core recipes are intentionally narrower than the Spring composite. Unsupported application features must fail preflight or be reported outside coverage, not silently "migrated". Do not extrapolate fixture success to a company monorepo.
4. **Verifier integrity versus OS isolation:** the supported mock cannot edit verification artifacts through the tool gateway. Arbitrary executable agents or hostile Java code are outside the guarantee. POM/test/hash enforcement is testable, not a general security claim.
5. **Offline resolution:** Maven and Rewrite may need cached POM metadata as well as JARs. Cache preparation must run both baseline/target and recipe paths, then prove `--offline` with actual commands. A failure becomes a concrete M1/M2 blocker.
6. **Windows:** subprocess termination, paths with spaces, and case-insensitive/reparse path handling need local tests; no assumption that Linux process-group behavior works here.
7. **Evidence limits:** mocked agent behavior, observed tool execution, and recorded replay remain separate fields. No paid calls or model benchmark claims. Durations are observations from this machine only.

No essential human input remains for planning. Next required action is **main's accepted-plan/M1 instruction plus a build lease**, not a credential or paid account.

## 9. Primary-source read log

Each URL was opened/read on 2026-10-07. Short findings are recorded instead of copying upstream prose. Repository/POM HTTP checks supplement documentation reads.

| Source | Finding used |
| --- | --- |
| https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.0-Migration-Guide | Java 17/Jakarta changes, trailing-slash default, explicit route option, property rename |
| https://docs.spring.io/spring-boot/docs/3.0.13/reference/html/getting-started.html#getting-started.system-requirements | Exact target Java 17 and Maven compatibility |
| https://docs.openrewrite.org/reference/rewrite-maven-plugin | Real run/dryRun lifecycle and patch behavior |
| https://docs.openrewrite.org/recipes/maven/upgradeparentversion | Exact version option, Apache license |
| https://docs.openrewrite.org/recipes/java/changepackage | Package/import transformation, Apache license |
| https://docs.openrewrite.org/recipes/properties/changepropertykey | Properties-key transformation, Apache license |
| https://raw.githubusercontent.com/openrewrite/rewrite/v8.41.0/rewrite-maven/src/main/java/org/openrewrite/maven/UpgradeParentVersion.java | Pinned recipe source exists and Apache header |
| https://raw.githubusercontent.com/openrewrite/rewrite/v8.41.0/rewrite-java/src/main/java/org/openrewrite/java/ChangePackage.java | Pinned recipe source exists and Apache header |
| https://raw.githubusercontent.com/openrewrite/rewrite/v8.41.0/rewrite-properties/src/main/java/org/openrewrite/properties/ChangePropertyKey.java | Pinned recipe source exists and Apache header |
| https://docs.openrewrite.org/reference/latest-versions-of-every-openrewrite-module | Current distribution/authentication and module-license inventory |
| https://docs.openrewrite.org/licensing/openrewrite-licensing | Apache core building blocks versus source-available modules |
| https://docs.openrewrite.org/recipes/java/spring/boot3/upgradespringboot_3_5-community-edition | Current Spring recipe identity; not selected |
| https://github.com/spring-projects-experimental/spring-boot-migrator | Existing migration CLI and OpenRewrite overlap |
| https://github.com/SWE-agent/SWE-agent | Existing tool-using editing harness and successor reference |
| https://maven.apache.org/tools/wrapper/ | Scoped pinned wrapper, Windows entry point, first-run download |
| https://maven.apache.org/docs/3.9.11/release-notes.html | Maven 3.9.11 release availability |
| https://raw.githubusercontent.com/openrewrite/rewrite/v8.41.0/LICENSE | Pinned core Apache license |
| https://raw.githubusercontent.com/openrewrite/rewrite-maven-plugin/v5.46.0/LICENSE | Pinned plugin Apache license |
| https://raw.githubusercontent.com/spring-projects/spring-boot/v3.0.13/LICENSE.txt | Target Spring Boot Apache license |
| https://raw.githubusercontent.com/google/gson/gson-parent-2.13.2/LICENSE | Gson Apache license |
| https://raw.githubusercontent.com/junit-team/junit5/r5.11.4/LICENSE.md | JUnit EPL-2.0 license |
| https://raw.githubusercontent.com/apache/maven/maven-3.9.11/LICENSE | Maven Apache license |
| https://raw.githubusercontent.com/apache/maven-wrapper/maven-wrapper-3.3.4/LICENSE | Wrapper Apache license |
| https://repo.maven.apache.org/maven2/org/openrewrite/maven/rewrite-maven-plugin/5.46.0/rewrite-maven-plugin-5.46.0.pom | Published plugin pin and Rewrite BOM 8.41.0 |
| https://repo.maven.apache.org/maven2/org/openrewrite/recipe/rewrite-spring/5.22.0/rewrite-spring-5.22.0.pom | Historical Apache module; broader than needed |
| https://raw.githubusercontent.com/openrewrite/rewrite-spring/v5.22.0/src/main/resources/META-INF/rewrite/spring-boot-30.yml | Historical composite has floating selectors and broad chained recipes; not selected |
| https://repo.maven.apache.org/maven2/org/openrewrite/maven/rewrite-maven-plugin/maven-metadata.xml | Central public inventory distinct from current docs |
| https://repo.maven.apache.org/maven2/org/openrewrite/recipe/rewrite-spring/maven-metadata.xml | Central public Spring inventory |
| https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip.sha512 | Distribution checksum read without downloading archive |

Planning evidence: read AGENTS.md, assignment, status, user authorization and resources; inspected Git status; ran `java -version`, `python --version`, `git --version`, and `Get-Command mvn`; read primary pages/POMs/metadata and checked small artifact HEAD requests. A guessed old recipe docs URL and a guessed tag returned 404; corrected URLs/published POMs were read. One initial Maven distribution coordinate contained a duplicated path segment and returned 404; the corrected exact URL returned 200. No Maven builds, application tests, benchmark, archive downloads, real model calls, CI runs, or publication have occurred.
