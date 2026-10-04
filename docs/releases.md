# PR and release process

Normal PR → Android and automation checks → squash merge → prepare release PR → approve its workflows and pass CI → squash merge release PR → release → tag, checked APK and publication.

Release preparation and publication are manual workflow dispatches. There is no workflow that merges PRs and no release work on every push. GitHub's native auto-merge stays disabled. An agent can carry out the short prompts below using the maintainer's GitHub login.

## Everyday PRs

1. Open a PR against `main`. Android CI runs on GitHub's merge commit, testing the PR together with its base. The `build` check assembles debug, runs unit tests, verifies Roborazzi screenshots, and runs lint. `Automation checks` validates workflows, release code, version policy and release-file consistency.
2. Fix failures; do not skip tests, rewrite baselines without inspecting differences, or use an administrator bypass. Failure reports are available as workflow artifacts for 14 days.
3. Squash-merge after both required checks pass and the branch is current with `main`. If the base advances, update the branch and wait for the new checks. Resolve review conversations. No second-person approval is required for this single-maintainer repository.
4. CI runs again on `main`. Nothing prepares or publishes a release yet.

Use a conventional PR title because it becomes the squash commit: `fix:` and `feat:` request a patch; `!` or `BREAKING CHANGE:` requests a minor; `feat(major):` requests a major for a complete new version. `docs:`, `chore:` and `ci:` alone do not request a release.

## Prepare release

Run from `main`:

```sh
gh workflow run release-please.yml --ref main -f operation=prepare
```

Release Please reads merged conventional commits and opens or updates `release-please--branches--main`. It owns exactly `version.txt`, `.release-please-manifest.json` and `CHANGELOG.md`. It does not merge, tag or publish. If a merged release PR still needs releasing, preparation stops and tells you to release it first. If there are no releasable changes, there may be no PR or no update to an existing PR; consult the run summary.

The built-in token opens/updates the PR. GitHub may require a maintainer to select **Approve workflows to run** in the PR before its normal CI starts. An agent can approve the specific run using:

```sh
gh api --method POST repos/skychaze/Aftertaste/actions/runs/RUN_ID/approve
```

Approve only after inspecting the release diff. Do not dispatch a second CI workflow to work around this approval: it does not approve the original run. Any bot update may require approval again. A fork contributor's separate approval requirement remains enabled too.

## Merge release

Inspect the release version, changelog and three-file diff. Ensure the required checks pass on the current revision and base. Squash-merge using the inspected head SHA:

```sh
gh pr merge PR_NUMBER --squash --match-head-commit HEAD_SHA
```

If main has advanced and preparation made no file changes, use `gh pr update-branch PR_NUMBER`, inspect the refreshed diff and wait for new PR CI. A maintainer-authored branch update triggers normal PR CI.

Do not use `--admin` or bypass failed/pending checks. This uses the maintainer/agent login, not a workflow token. The merge runs normal `main` CI. It does not create a tag or publish. Wait for that CI before releasing. Do not merge a second release PR before releasing the first.

## Release

```sh
gh workflow run release-please.yml --ref main -f operation=release
```

Release Please finds the merged release PR, creates `v<version>` at **that PR's merge commit**, and creates a draft release with generated notes. It does not prepare the next PR. Forced tag creation is necessary because an ordinary draft release may not materialize its tag yet.

The same run calls the APK workflow explicitly. This is necessary because a tag created with `GITHUB_TOKEN` does not trigger the tag-push workflow. There is no dependency on a `release: published` event.

The APK job checks out the tag, validates its version, runs unit tests, screenshots and lint without the production Last.fm key, then injects the key and builds with the release keystore. It verifies the package, version, APK signature and expected signing certificate, uploads `aftertaste-v<version>-<versionCode>.apk`, and publishes the draft without replacing its notes. The app updater can then discover it.

Version code is `major * 1000000 + minor * 1000 + patch`; each part is 0–999 and the code must increase. CI rejects inconsistent version/manifest/changelog changes. Do not manually edit release-owned files in ordinary PRs.

## Recovery

- Failed preparation: fix its reported configuration/API problem and dispatch `prepare` again. No merge or publication has happened.
- Release PR CI failed: inspect the reports, fix the underlying code on a normal PR, merge it, then run `Prepare release` to refresh the release PR and rerun its checks. Do not bypass CI.
- Draft/tag exists but APK failed, or a release-creation run partially completed: inspect the tag/draft, then retry the current APK workflow at that existing tag:

  ```sh
  gh workflow run release.yml --ref main -f tag=v1.2.12
  ```

  This uses the repaired workflow on `main` while checking out the tagged app source. Rerunning an old workflow run uses its old workflow definition instead. Notes and tag stay unchanged. An old source defect needs a new version; do not move the tag. Published releases are not overwritten. Publication also rejects a version older than any published version and a tag outside main history.
- An already-published version needs a fix: open a normal fix PR and prepare a new release.
- A manual APK build with an empty tag uploads an artifact only; it does not publish. A human-pushed `v*` or `V*` tag still builds/publishes, but it must match `version.txt`. Prefer Release Please to retain consistent release metadata.
- Historical `v1.2.8` is an intentionally unpublished, superseded draft after its tests failed. Leave it as historical evidence; do not publish it over newer releases.

## Short agent prompts

| Prompt | Predictable operation |
|---|---|
| **Check CI** | Inspect the current PR's latest checks and any approval gate; explain failures. Do not merge or publish. |
| **Fix CI** | Read failure logs/artifacts, fix the cause, validate, and update the PR. Preserve meaningful checks. |
| **Merge PR** | Check diff/title/current SHA, required CI, current base and conversations; squash-merge with the head guard. |
| **Prepare release** | Check main CI, dispatch `prepare`, inspect the resulting PR, approve its expected bot workflow if needed, and report CI. Stop before merge. |
| **Merge release** | Inspect the three-file release diff; approve expected workflows if needed; after required CI passes, squash-merge with the head guard. Stop before publication. |
| **Release** | Check the merged release PR and main CI; dispatch `release`; verify the resulting tag, notes and signed APK publication. Do not merge an unchecked PR. |
| **Retry release vX.Y.Z** | Inspect the existing unpublished draft/tag; dispatch `release.yml` on main with that tag. Never move a tag or replace a public APK. |

If no PR is specified, use the current branch's open PR; for release operations, use the single release PR. If there is ambiguity, report candidates instead of guessing. When T3's PR watcher is available, use it to resume after checks finish instead of starting duplicate work.

## Repository settings and maintenance

`main` requires PRs, `build` and `Automation checks` from GitHub Actions, a current base, resolved conversations and linear history. Administrators are included; force pushes and deletions are blocked. Squash is the only merge method. Native auto-merge is off. Release/publishing jobs must **never** become required PR checks: they occur after merge.

Workflow tokens default to read-only. The Release Please job alone requests contents/issues/pull-request write; APK publication requests contents write. The Actions setting allowing workflows to create PRs must remain enabled. No PAT, auto-approval bot, merge queue or duplicate branch-CI dispatch is required.

Signing requires `ANDROID_KEYSTORE_BASE64`, `ANDROID_STORE_PASSWORD`, `ANDROID_KEY_PASSWORD`, and `LASTFM_API_KEY`; `ANDROID_KEY_ALIAS` defaults to `upload`. Never print values. Preserve the existing keystore, application ID and asset naming contract.

Actions are pinned to immutable commits. Update pins deliberately and rerun actionlint and PR CI. `.github/release/package-lock.json` pins Release Please and TypeScript dependencies. For release changes run:

```sh
npm ci --prefix .github/release
npm run typecheck --prefix .github/release
npm test --prefix .github/release
npm run validate --prefix .github/release
actionlint
./gradlew help
```

For Android changes also run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:verifyRoborazziDebug :app:lintDebug`. Report unavailable checks accurately.
