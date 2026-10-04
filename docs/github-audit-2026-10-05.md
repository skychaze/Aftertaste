# GitHub configuration audit — 2026-10-05

The read-only audit was completed and reported before edits. Baseline: `a6a8e90ec71d9a764353829866eeebffbf953544` (`main`, release 1.2.11). This report distinguishes logged causes, historical fixes already present, and risks inferred from configuration. The implementation and validation record follows the findings; the operating guide is [releases.md](releases.md).

## Scope and settings before repair

Inspected all three workflow YAML files, their git history, `.github/release/` implementation, tests and npm lockfile, `release-please-config.json`, version/manifest/changelog, Android Gradle version/signing configuration, README, AGENTS.md and both repository verification skills. Inspected the latest 100 runs, PR history, relevant job logs, releases/assets and current GitHub settings through authenticated REST/CLI calls. No secret values were read.

- Workflows: Android CI, Release Please, Release APK; GitHub also lists the dynamic Copilot cloud-agent workflow. No repository composite Actions, Dependabot/Renovate configuration or CODEOWNERS file exists.
- Rulesets: none. Effective rules on main: none. Classic main protection: HTTP 404, branch not protected. No protected branches were returned.
- No required status checks, merge queue or review requirements. Native auto-merge disabled. Squash, merge-commit and rebase merging all allowed. Branch deletion after merge disabled.
- Actions enabled, all actions allowed, SHA pinning not required; default workflow token permissions read-only; Actions PR creation/approval allowed. First-time contributor workflow approval enabled.
- No repository webhooks or Actions variables. Only environment: `copilot`, with no protection rules. There is no release environment approval dependency.
- Secret names present: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`, `ANDROID_STORE_PASSWORD`, `LASTFM_API_KEY`. Their correctness is supported by successful recent signed publications, not inferred merely from existence.
- Latest published release: `v1.2.11`, asset `aftertaste-v1.2.11-1002011.apk`. `v1.2.8` remains an asset-less draft. Later releases 1.2.9–1.2.11 are published. Old uppercase `V` tags are intentionally supported.

## Failure chronology and causes

### 1. PR #23 introduced Release Please and the token workaround

[PR #23](https://github.com/skychaze/Aftertaste/pull/23) added conventional-commit versioning, a draft/tag creation path and a reusable APK call. Commit `3927ab3` added a separate CI dispatch for bot-created PRs. The custom strategy in `.github/release/versioning.ts` intentionally treats ordinary features as patch bumps, breaking changes as minor, and `feat(major)` as major. Its tests support that policy; this is not a semver configuration accident.

The independent CI dispatch was understandable under older assumptions about workflow-token events. Current [GitHub event rules](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/trigger-a-workflow) explicitly create approval-required PR workflows for token-authored opened/synchronize/reopened events. Dispatch runs are separate; they do not approve the PR event run. Other token-generated events, including tag pushes, remain suppressed. Thus an explicit reusable publishing call is correct, while a duplicate CI dispatch produces confusing status and approval behavior.

Evidence: bot release-PR run [37151746698](https://github.com/skychaze/Aftertaste/actions/runs/37151746698) remains `action_required`. Runs [37152496359](https://github.com/skychaze/Aftertaste/actions/runs/37152496359) and [37183814872](https://github.com/skychaze/Aftertaste/actions/runs/37183814872) concluded failure with zero jobs and no logs; their separately dispatched counterparts [37152496516](https://github.com/skychaze/Aftertaste/actions/runs/37152496516) and [37183815661](https://github.com/skychaze/Aftertaste/actions/runs/37183815661) passed. The no-job API records do not expose the exact terminal reason; they are not evidence of a Gradle failure. The documented approval behavior and adjacent action-required run establish the approval problem without inventing an unavailable error message.

Fix: one PR workflow; inspect and approve the bot's expected workflow run. Remove automatic branch dispatch and reusable PR-CI invocation. Keep explicit APK invocation after tag creation.

### 2. Release 1.2.8 had a release-only screenshot failure

[Run 37143444994](https://github.com/skychaze/Aftertaste/actions/runs/37143444994), job `111262523429`, failed `LikedMusicTest > saved songs remain available in empty taste periods and open a music search` at its screenshot assertion (line 129). Eighty-five tests ran; one failed. The release workflow injected `.env` with the production Last.fm key before debug tests, unlike ordinary CI. That changed the configuration rendered by the genre UI.

[PR #27](https://github.com/skychaze/Aftertaste/pull/27), commit `70f5823`, moved Last.fm injection after Android checks. Preserve that fix: checks use the deterministic debug configuration, while the signed APK retains real Last.fm functionality. The 1.2.8 draft correctly remained unpublished, and newer releases superseded it. Do not repair it by publishing an old version over current releases.

### 3. PR #29's automatic merge search used the wrong label

[PR #29](https://github.com/skychaze/Aftertaste/pull/29), commit `bdedba2`, searched for `--label autorelease:pending`. Release Please uses `autorelease: pending`, including the space. No matching PR set the output; `check-release-pr`, `merge-release-pr` and publishing were consequently skipped. This was a silent orchestration failure, not a rejected merge. `feb3b5b` in [PR #34](https://github.com/skychaze/Aftertaste/pull/34) corrected the label.

Even after correction, [run 37183796224](https://github.com/skychaze/Aftertaste/actions/runs/37183796224) created PR #36 at 06:47:03 UTC, applied the label at 06:47:04, dispatched CI, then found nothing with `gh pr list --author ... --label ...`. It ended successfully with all dependent jobs skipped. A later manual dispatch found the same PR. This is consistent with eventual consistency of GitHub search. The missed lookup is proven; GitHub's internal indexing cause cannot be proven from client logs.

Fix: remove the automatic merge search/chain. Preparation reports the PR returned by Release Please. No downstream work relies on immediate search visibility.

### 4. Actual screenshot regressions reached main without a merge gate

[PR #30](https://github.com/skychaze/Aftertaste/pull/30) failed [37149552725](https://github.com/skychaze/Aftertaste/actions/runs/37149552725) and [37150290165](https://github.com/skychaze/Aftertaste/actions/runs/37150290165). The attempted fix in [PR #31](https://github.com/skychaze/Aftertaste/pull/31) failed [37151264432](https://github.com/skychaze/Aftertaste/actions/runs/37151264432). All identify `AnalyticsScreenshotTest.dailyListeningScreenshot` at line 109. Both PRs merged; [main run 37151719310](https://github.com/skychaze/Aftertaste/actions/runs/37151719310) and the release branch then failed the same assertion.

Root cause: the screenshot used a real date, and its playback-controls fixture did not match the committed golden. PR #31 changed repeat capability in the wrong direction and did not freeze the date. PR #34 added an injectable date label with a fixed test date and restored the matching repeat capability. Those corrections are already present and subsequent screenshots pass. No baseline or test should be removed.

Separate configuration cause: main had no required checks, so failures did not prevent merges. Fix that with required Android and automation checks, current-base testing, PR-only squash merging and enforcement for administrators. This strengthens safety rather than bypassing it.

### 5. October 4's apparent merge failure was a test failure

[Run 37184860906](https://github.com/skychaze/Aftertaste/actions/runs/37184860906), job `111384618549`, failed `DailyListeningViewTest > goal editing is collapsed until requested and the launch action opens music` with `CalledFromWrongThreadException` at `ViewRootImpl.java:11357` and `ArrayIndexOutOfBoundsException` at `SlotTable.kt:3975`. Ninety-four tests ran; one failed. The merge job never executed. [Run 37185656926](https://github.com/skychaze/Aftertaste/actions/runs/37185656926) subsequently passed, merged #36 using `github-actions[bot]`, and published 1.2.11.

This is an intermittent Compose/Robolectric threading failure, not a token, branch protection or native auto-merge failure. The old workflow retained no test XML/report artifact and printed only short test exceptions. The exact initiating call cannot be established from that evidence. Do not claim a dependency upgrade, skipped assertion or green retry proves it fixed. Retain the test, publish future failure reports, and keep this as an explicitly unresolved test-stability risk unless a reproducible stack establishes a targeted repair.

## Other confirmed configuration gaps

| Area | Responsible code/settings and consequence | Repair |
|---|---|---|
| Too many release paths | `release-please.yml` runs main preparation, dispatches branch CI, calls reusable CI, merges, runs Release Please again and calls APK publication. One Android defect can appear in several runs with different status contexts. | Manual `prepare` and `release` operations; human/agent merge between them. |
| Mixed mutations | `run.ts` called both `createReleases()` and `createPullRequests()` every time, including after a bot merge. A retry's meaning depended on intermediate state. | Each operation performs exactly one kind of mutation; preparation refuses an unreleased merged PR. |
| No premerge automation validation | TypeScript and release tests ran only in the write-enabled release job. Android CI ignored workflow syntax and release code. | Required `Automation checks`: actionlint, typecheck, operation tests, simulated updates, version/config validation and release-file allowlist. |
| Missing failure evidence | Android/release workflows discarded XML, screenshots and lint reports. | Failure artifact uploads with 14-day retention. |
| Incomplete APK verification | `release.yml` checked package/version via aapt2; Gradle required signing credentials, but the final APK signer was not explicitly compared with that keystore. | `apksigner verify` plus exported-keystore certificate SHA-256 comparison before publication. |
| Public asset replacement | `gh release upload --clobber` could replace an APK even after publication. Tag/version checks did not enforce main ancestry or monotonic publication relative to published releases. | Refuse published versions, reject versions not newer than published tags, require tagged commits on main, resolve explicit tag refs; clobber remains only for unpublished draft recovery. |
| Runtime maintenance | Logs warn v4 Actions target deprecated Node 20 and are forced to Node 24; mutable major refs allowed upstream changes without repository review. No observed failure is attributed to this warning. | Update official Actions to current release commits and pin their SHAs; validate via hosted CI. |
| Instruction drift | AGENTS/README describe automatic merging and duplicate dispatch as mandatory. Both skill mirrors misleadingly associate `assembleDebug` alone with full CI. | New release guide, concise repository rules, short operation prompts, corrected build/test distinction in both mirrors. |

The npm production dependency audit returned zero known vulnerabilities. The lockfile and custom version policy remain. `draft: true` plus `force-tag-creation: true` is intentional: it provides a checkout-able tag while withholding public release until APK checks pass. See [Release Please manifest documentation](https://github.com/googleapis/release-please/blob/main/docs/manifest-releaser.md).

## Conflicts and circular dependencies

No current ruleset/branch-protection conflict caused the historical failures: there were no such rules. There is no release-event workflow and no environment approval circle. The existing explicit reusable APK call correctly handles token-generated tags.

The old reusable PR-CI invocation checked out the PR head but its workflow run belonged to the main caller; checkout does not change a check run's association. Making that caller job a required PR check would be fragile. Requiring release/publishing before PR merge would be circular because those operations need a merged release PR. The repair requires only `build` and `Automation checks`, both produced by the PR workflow. Release jobs are never required PR checks.

The old file allowlist was valuable, but an exact PR-head guard alone did not guard against main advancing while testing the head in isolation. Strict branch protection plus CI on GitHub's PR merge ref provides the integration gate.

## Repair and validation record

See the final task response and PR for hosted run URLs and deployment status. Local checks: actionlint 1.7.12 (release archive verified against published checksum), TypeScript checking, Release Please version/operation simulations, configuration validation, `git diff --check`, and npm production audit. Main's release-owned files are unchanged by this repair.

The initial local Gradle configuration attempt could not start: HP has no Java executable/JAVA_HOME or Android SDK. Lenovo SSH timed out. Hosted Android CI is required for the full Android build/tests/screenshots/lint check. No live release was created merely to test this repair; signing/publication changes must be exercised by the next explicitly requested release or an artifact-only signed build when authorized.
