# AfterTaste

## Releases

Release Please maintains the release PR from conventional commits on `main`. Use `fix:` for patch releases, `feat:` for minor releases, and `!` or `BREAKING CHANGE:` for major releases. Use these prefixes in squash merge titles too.

Release Please owns `version.txt`, `.release-please-manifest.json`, and `CHANGELOG.md`. Let the release PR update them. Gradle reads the version name from `version.txt`. The Android version code is `major * 1000000 + minor * 1000 + patch`, with each part between 0 and 999. Keep version codes increasing across releases. Existing Gradle property and environment overrides remain available for verification builds.

Merge the release PR to create a `v<version>` tag and draft GitHub release. The Release Please workflow calls `.github/workflows/release.yml` to build and verify the signed APK, attach it, and publish the release. Keep the release as a draft if the build fails. Retry the release workflow manually with the existing tag. Preserve the generated release notes during retries. A manual run with no tag uploads a workflow artifact without publishing a release.

Read both release workflows and `release-please-config.json` before changing release behavior. Preserve the `aftertaste-v<version>-<versionCode>.apk` name, package identity, and signing checks because the in-app updater depends on them. Manual tags must match `version.txt`; both lowercase `v` and uppercase `V` are accepted.

With the built-in GitHub token, enable "Allow GitHub Actions to create and approve pull requests" in repository Actions settings. The Release Please workflow dispatches Android CI on each created or updated release PR branch. Keep its `actions: write` permission and the CI workflow dispatch trigger so checks run without a separate token. The APK workflow also runs unit tests, screenshot checks, and lint before publishing.

Validate release changes with actionlint, simulated Release Please version updates, and a Gradle configuration check. Run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:verifyRoborazziDebug :app:lintDebug` when changing Android behavior. Report any checks that could not run and the reason.
