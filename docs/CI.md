# Prepared CI and delivery checks

The workflow is prepared locally, not pushed, dispatched or claimed to have run. It uses standard `ubuntu-24.04` and `windows-2025` GitHub-hosted runner labels with matrix `max-parallel: 1`, a 45-minute job limit, and cancellation of superseded runs. Runner images are platform-managed and are not immutable images. Temurin Java `17.0.18+8` is an exact requested distribution version; local evidence uses the existing Microsoft Java 17.0.18 instead.

The following action tag-to-commit mappings were read from official GitHub APIs on **2026-10-08**. The workflow uses full immutable commit IDs; saved provenance is [.github/action-pins.json](../.github/action-pins.json).

| Action | Release | Immutable commit | Primary source |
| --- | --- | --- | --- |
| `actions/checkout` | v7.0.1 | `3d3c42e5aac5ba805825da76410c181273ba90b1` | [Official release](https://github.com/actions/checkout/releases/tag/v7.0.1) |
| `actions/setup-java` | v6.0.1 | `de7274f081f381c8f8158605e0321c36c376e2e6` | [Official release](https://github.com/actions/setup-java/releases/tag/v6.0.1) |

Checkout has `contents: read`, `persist-credentials: false` and a shallow fetch. Git configuration is supplied through environment variables for byte-preserving line endings without writing the local user's global settings. Triggers are push, ordinary pull request and manual dispatch; no privileged pull-request trigger, secret input, registry publication, deployment, paid model call or artifact-upload action is configured. No CI account or workflow was changed in this local task.

Each job runs an online wrapper `clean verify`, then `scripts/demo.ps1 -Extended`. Online preparation executes actual baseline/recipe/target paths and warms the repository cache. All comparison, replay and failure paths then require actual Maven `-o`; an empty-cache case must fail with the uncached Boot parent rather than retry online. The demo checks physical manifests, Surefire XML, HTTP literals, fresh verification directories, patch bytes and native process inventory. The existing unit suite covers authority/rollback/stale/malformed/no-op/repeat/budget cases and controlled child timeout/interruption. These are sequential Maven/app runs with 2 GiB direct/test-fork heap limits. `package-review.ps1` then assembles source, CLI and notices with ZIP-entry SHA-256 checks.

This is a free public-repository CI design using standard runners. [GitHub's billing documentation](https://docs.github.com/en/billing/concepts/product-billing/github-actions) states public-repository standard hosted runners are free; private-repository quotas and larger runners have different billing. No hosted execution occurred, and no future account billing state is assumed.

## Static and local coverage

Workflow/action metadata, PowerShell parsing, immutable pins, permissions, sequential limits, forbidden publication/secret features, source links, catalog checks and review-package hashes can be checked without a heavy build. Actual final local build/demo results belong in `EVIDENCE.md`. Final local static parsing observes bundled PowerShell 7.6.5 on Windows; Windows PowerShell 5.1 was not separately validated. CI uses installed PowerShell 7 on both OS runners. The POSIX wrapper and Linux process-inventory branch remain unexecuted locally until a corresponding run provides evidence.

No broad platform-support guarantee follows from a prepared workflow. A cancellation/OS force-kill may interrupt report generation; the tested Java interruption path cleans captured children, but this does not guarantee hosted-runner cancellation behavior.
