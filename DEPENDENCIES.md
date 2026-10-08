# Dependency attribution

Runtime/build/fixture evidence retrieved/audited on **2026-10-07**; M3 catalog packaged and external CI action pins retrieved on **2026-10-08**. Project code is Apache-2.0. Dependencies retain their own licenses. The shipped [399-artifact catalog](third-party/dependency-inventory.json) retains tested-cache coordinates, byte counts, SHA-256, licenses and exact source POM URLs; raw graphs remain outside the distributed CLI.

## Distributed with source or CLI

| Artifact | Version | License | Primary evidence |
| --- | --- | --- | --- |
| Apache Maven Wrapper scripts/JAR | 3.3.4 | Apache-2.0 | [Tagged LICENSE](https://raw.githubusercontent.com/apache/maven-wrapper/maven-wrapper-3.3.4/LICENSE), [NOTICE](https://raw.githubusercontent.com/apache/maven-wrapper/maven-wrapper-3.3.4/NOTICE) |
| Gson | 2.13.2 | Apache-2.0 | [Tagged LICENSE](https://raw.githubusercontent.com/google/gson/gson-parent-2.13.2/LICENSE) |
| Error Prone annotations (Gson transitive) | 2.41.0 | Apache-2.0 | [Tagged COPYING](https://raw.githubusercontent.com/google/error-prone/v2.41.0/COPYING) |

Original wrapper headers and `third-party/maven-wrapper-NOTICE` are retained. Local changes to wrapper scripts are marked in the scripts and NOTICE. Gson/Error Prone license copies are under `third-party/`. The shaded executable is a classpath CLI; Maven Shade reported overlapping module-info and manifest resources. Its manifest is generated for the CLI entry point. The real CLI/demo ran successfully; JPMS module use has not been tested.

## Externally resolved build/tool/fixture dependencies

Maven 3.9.11 is downloaded into `work/`; its distribution LICENSE/NOTICE are preserved by extraction. The ZIP matched upstream SHA-512. The wrapper distribution ZIP matched Central's published SHA-1 checksum; Central does not provide SHA-512 for that wrapper ZIP. Local SHA-256 fingerprints are additionally recorded in `work/research/bootstrap-integrity.json`; wrapper configuration checks the Maven distribution and wrapper JAR SHA-256.

OpenRewrite Maven plugin **5.46.0** uses its published BOM pin **8.41.0**. The actual recipes are Apache-licensed core Java/Maven/Properties building blocks. `rewrite-spring` is not a dependency. JUnit Jupiter **5.11.4** is test-only (EPL-2.0). The Boot fixture baseline **2.7.18** and target **3.0.13** are Apache-2.0; web, validation and test starters resolve their version-specific dependency graphs. Historical unsupported versions are intentional fixture pins, not deployment recommendations.

Actual cache audit: **399 JARs**, **277,657,216 JAR bytes**, **zero unresolved declared/inherited license records**, **zero Moderne/source-available recipe artifacts detected**. `scripts/audit-dependencies.ps1` inventories every JAR in `work/m2`, follows upstream POM parent license declarations, records exact source URLs and SHA-256, and emits:

* `outputs/dependency-audit/resolved-artifacts.json`
* `outputs/dependency-audit/summary.json`

The cache includes application, test, parser and Maven-plugin graphs, rather than 399 runtime dependencies of the CLI. Published versioned POMs and downloaded artifact hashes are retained locally. The full inventory is generated evidence rather than a hand-maintained list. `third-party/dependency-inventory.json` retains the M1/M2 catalog with source delivery. Conventional sources-JAR URLs are explicitly unverified location candidates; declared/inherited POM license-source URLs were actually read. A fresh audit on another OS may produce a different inventory and does not overwrite this historical catalog automatically.

The externally cached graphs include Apache/MIT/BSD/CC0/public-domain declarations and these material non-Apache cases:

* JUnit, Logback and Eclipse components have EPL/EDL declarations; Logback also declares LGPL alternatives.
* Jakarta annotation APIs 1.3.5/2.1.1 declare EPL-2.0 and GPL-2.0 with Classpath Exception.
* JNA/JNA-platform 5.7.0/5.15.0 declare LGPL and Apache alternatives.
* Checker compatibility qualifiers 2.5.5 declare GPL with Classpath Exception and MIT alternatives.
* `javax.annotation:jsr250-api:1.0` declares CDDL-1.0.
* JDOM 2.0.6/2.0.6.1 publishes a named custom license; the exact upstream POM URL is in the inventory. It is not relabelled Apache-2.0 here.

These external cached JARs are not packaged into the source repository or harness executable. If distributing a generated Spring application or repackaging the tool cache, retain and review its version-specific license and notice obligations separately. This audit covers upstream declarations and resolved fingerprints; it is not a vulnerability scan or a claim that every dependency is Apache licensed.

## External prepared-CI and delivery scope

Prepared CI uses `actions/checkout` v7.0.1 and `actions/setup-java` v6.0.1 with full official commit pins; both have MIT licenses retained under `third-party/ci-*-LICENSE`. Source/license URLs are commit-specific in `docs/CI.md` and `.github/action-pins.json`. They are not compiled into the CLI. CI requests Temurin Java 17.0.18+8 externally; neither its JDK nor GitHub's action distributions are packaged in the review ZIP. No action/provider/Java runtime was installed or executed locally for CI preparation.

The local review ZIP includes source/wrapper, the built harness JAR and adjacent project/Gson/Error Prone/wrapper notices and licenses. Do not redistribute the JAR stripped of accompanying notices. It excludes cached Maven/OpenRewrite/application/test JARs and generated Spring application JARs. Distribution of excluded binaries requires separate version-specific obligations. Package manifests/ZIP hashes are review evidence, not a reproducible-build or legal certification.

M2 actual offline-cache proof and main independent acceptance are recorded in `EVIDENCE.md`; M3 local package/build/assertion execution and remaining hosted-CI/POSIX checks are distinguished there. No dependency/runtime was installed machine-wide. See [source provenance](docs/SOURCES.md) for tagged upstream sources and exact bootstrap hashes.
