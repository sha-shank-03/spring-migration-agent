# Source and provenance log

M1/M2 technical-source retrieval and artifact evidence are dated **2026-10-07** in `PLAN.md` and the bootstrap/catalog records. M3 CI source metadata was retrieved **2026-10-08**. Existing migration/dependency pins are preserved. New action pins are external prepared-CI dependencies and do not change Java runtime or fixture graphs.

| Area | Primary source | Use |
| --- | --- | --- |
| Spring Boot migration | [Official Boot 3.0 migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.0-Migration-Guide) | Historical Java 17/Jakarta boundary, route matching and header-property changes |
| Target requirements | [Boot 3.0.13 reference](https://docs.spring.io/spring-boot/docs/3.0.13/reference/html/getting-started.html#getting-started.system-requirements) | Exact pinned target requirements |
| Rewrite invocation | [Official Maven plugin docs](https://docs.openrewrite.org/reference/rewrite-maven-plugin) | Existing transformation engine |
| Package recipe | [Tagged ChangePackage source](https://github.com/openrewrite/rewrite/blob/v8.41.0/rewrite-java/src/main/java/org/openrewrite/java/ChangePackage.java) | Apache core building block |
| Parent recipe | [Tagged UpgradeParentVersion source](https://github.com/openrewrite/rewrite/blob/v8.41.0/rewrite-maven/src/main/java/org/openrewrite/maven/UpgradeParentVersion.java) | Exact parent upgrade |
| Property recipe | [Tagged ChangePropertyKey source](https://github.com/openrewrite/rewrite/blob/v8.41.0/rewrite-properties/src/main/java/org/openrewrite/properties/ChangePropertyKey.java) | Selected property rename |
| Rewrite license | [Tagged core license](https://github.com/openrewrite/rewrite/blob/v8.41.0/LICENSE), [plugin license](https://github.com/openrewrite/rewrite-maven-plugin/blob/v5.46.0/LICENSE) | Chosen Apache pins, distinct from source-available Spring recipe distributions |
| Wrapper | [Tagged wrapper source](https://github.com/apache/maven-wrapper/tree/maven-wrapper-3.3.4), [official wrapper docs](https://maven.apache.org/tools/wrapper/) | Provenanced scripts/JAR; modifications marked |
| Existing migration tool | [Spring Boot Migrator](https://github.com/spring-projects-experimental/spring-boot-migrator) | Attribution for prior Spring migration work |
| Existing agent harness | [SWE-agent](https://github.com/SWE-agent/SWE-agent) | Attribution for editing-agent harnesses |
| Actions checkout pin | [Official tag API](https://api.github.com/repos/actions/checkout/git/refs/tags/v7.0.1) | Commit `3d3c42e5aac5ba805825da76410c181273ba90b1` |
| Actions Java pin | [Official tag API](https://api.github.com/repos/actions/setup-java/git/refs/tags/v6.0.1) | Commit `de7274f081f381c8f8158605e0321c36c376e2e6` |
| Java distribution | [Adoptium version API](https://api.adoptium.net/v3/assets/version/17.0.18%2B8?architecture=x64&heap_size=normal&image_type=jdk&jvm_impl=hotspot&os=linux&vendor=eclipse) | Metadata identifies `jdk-17.0.18+8`; no JDK downloaded locally |
| CI semantics | [GitHub workflow syntax](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax), [billing](https://docs.github.com/en/billing/concepts/product-billing/github-actions) | Read permissions, serial matrix, timeout and standard-public-runner billing scope |

`third-party/dependency-inventory.json` retains all 399 actually cached artifact coordinates, fingerprints, sizes, declared/inherited licenses and exact POM license-source URLs. Conventional Central sources-JAR URLs are source-location candidates, explicitly not downloaded or availability-verified. This is a full catalog of the tested Windows cache graph, not a claim that all artifacts ship in the CLI or that another OS will resolve an identical graph. Tagged license copies cover distributed wrapper/Gson/Error Prone and external CI action attribution; caches, JDKs, Maven distributions and generated fixture applications are excluded from the review ZIP.

`third-party/bootstrap-integrity.json` retains actual archive checksum receipts; `.mvn/wrapper/maven-wrapper.properties` enforces Maven distribution/wrapper JAR SHA-256. The original wrapper ZIP only supplied SHA-1 through Central, which matched, and a local SHA-256 was recorded. `LICENSE`, `NOTICE` and `DEPENDENCIES.md` distinguish project licensing from upstream graph obligations. No license, vulnerability, source/binary equivalence or current-release suitability certification is claimed.
