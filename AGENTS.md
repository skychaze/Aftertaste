# AfterTaste

## GitHub and releases

Read [docs/releases.md](docs/releases.md) for the exact process and short agent prompts: **Check CI**, **Fix CI**, **Merge PR**, **Prepare release**, **Merge release**, **Release**, and **Retry release vX.Y.Z**.

Normal PR → required Android and automation CI → maintainer/agent squash merge → manual release preparation → release PR CI and squash merge → manual release → tag and checked APK publication. No workflow automatically merges PRs. Preparation and release are separate dispatch operations; neither runs on every push.

Use `fix:` and `feat:` for patch releases, `!` or `BREAKING CHANGE:` for minor releases, and `feat(major):` only for a complete new version that should bump major. `docs:`, `chore:` and `ci:` alone do not trigger a release. Use these prefixes in squash merge titles.

Release Please owns `version.txt`, `.release-please-manifest.json` and `CHANGELOG.md`. Only these three files may change in a release PR; ordinary PRs leave them to Release Please. Gradle reads `version.txt`. Android version code is `major * 1000000 + minor * 1000 + patch`; parts are 0–999 and codes must increase. Existing Gradle property/environment overrides remain available for verification builds.

Read all three workflows, `.github/release/` and `release-please-config.json` before changing releases. Keep required checks named `build` and `Automation checks`, or update branch protection in the same migration. Never require a release/publish job before merging a PR. Never bypass failed or pending checks with administrator merge.

Bot-created release PR workflows may need maintainer approval. Inspect their three-file diff and approve the existing PR run; do not launch duplicate CI as a workaround. Keep the Actions setting allowing workflows to create PRs enabled. `GITHUB_TOKEN` tag pushes do not start another workflow, so Release Please explicitly calls the APK workflow after creating a draft/tag.

Preserve `aftertaste-v<version>-<versionCode>.apk`, package identity and signing certificate checks: the in-app updater depends on them. Both `v` and `V` tags are accepted and must match `version.txt`. Keep a release draft if checks/build/signing fail. Retry the current APK workflow on main with the existing tag, preserving notes. Never move a tag or overwrite a published APK. A manual APK run without a tag uploads an artifact only.

Validate release changes with actionlint, release typechecking/tests (including simulated updates), configuration validation and `./gradlew help`. Run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:verifyRoborazziDebug :app:lintDebug` when changing Android behavior. Preserve screenshot checks and inspect diffs before changing baselines. Report checks that could not run and why.
