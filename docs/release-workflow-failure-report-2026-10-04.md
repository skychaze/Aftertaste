# AfterTaste release and CI failure report

**Prepared:** 2026-10-04 07:32 UTC  
**Repository:** [skychaze/Aftertaste](https://github.com/skychaze/Aftertaste)  
**Scope:** GitHub Actions and PR activity from the merge of [PR #23](https://github.com/skychaze/Aftertaste/pull/23), which introduced Release Please, through publication of v1.2.11. This report covers observed failures, blocked checks, and workflow behavior relevant to releases. It does not include failures before Release Please was added.

## Executive summary

The release process has had several separate problems. They should not be treated as one recurring failure:

1. A release APK build failed because a test screenshot was captured with release-only Last.fm configuration enabled.
2. Android CI repeatedly failed a screenshot test because its fixture did not fully pin the rendered UI state. One attempted fix did not remove the failure; a later PR fixed both the playback fixture and date.
3. The release automation originally needed a separate automatic merge stage, and later had a release-PR label mismatch. Both required workflow changes.
4. On the v1.2.11 run, Release Please created its PR and queried for it almost immediately. The query returned no PR, so the workflow marked its job successful while silently skipping the CI, merge, and APK jobs.
5. A subsequent CI retry failed once in a Compose/Robolectric test with UI-thread and slot-table exceptions. The same unit suite passed locally and on the next CI attempt.

The result was repeated manual diagnosis and retries. The latest release did complete: PR #36 merged, v1.2.11 was published, and the signed APK passed identity verification and was uploaded. The release workflow still needs a fix for the create-then-query race so a future run cannot report success while skipping publication.

## Release flow added by PR #23

[PR #23](https://github.com/skychaze/Aftertaste/pull/23) merged at 2026-10-03 11:07 UTC. It added Release Please, release version management, Android CI dispatch for bot-created release PRs, and the signed APK workflow. Its intended path is:

1. A conventional commit on `main` opens or updates a Release Please PR.
2. Android CI tests the generated PR commit.
3. The workflow squash-merges the release PR and creates a tag and draft release.
4. The signed APK workflow runs tests, screenshot verification, lint, release signing, package/version verification, and uploads the APK before publishing the release.

[PR #29](https://github.com/skychaze/Aftertaste/pull/29) later added the automatic merge stage. Its description records that the built-in GitHub token does not trigger a follow-up push workflow when it merges a bot-authored PR. The workflow therefore needs to finish release creation in the same run.

## Failures and workflow problems

Times below are UTC. Each Actions link opens the recorded run.

| Date | PR or run | Observed result | Cause and resolution |
|---|---|---|---|
| Oct 3 18:14 | [Run 37143444994](https://github.com/skychaze/Aftertaste/actions/runs/37143444994), release workflow after [PR #26](https://github.com/skychaze/Aftertaste/pull/26) | Release APK job failed at `:app:testDebugUnitTest`. `LikedMusicTest.saved songs remain available in empty taste periods and open a music search` failed at the Robolectric screenshot capture (`LikedMusicTest.kt:129`). | [PR #27](https://github.com/skychaze/Aftertaste/pull/27) identified that `LASTFM_API_KEY` was written to `.env` before screenshot tests. That changed `BuildConfig`-gated UI and added chart attribution to the screenshot. The workflow was changed to run checks before injecting the key for the release APK build. This addresses the documented cause. |
| Oct 3 18:29 | [Run 37144413663](https://github.com/skychaze/Aftertaste/actions/runs/37144413663), PR #27 Android CI | GitHub records failure, but no job details or logs are available now. | Exact cause cannot be confirmed from retained run data. PR #27 merged and the following release PR was created. |
| Oct 3 19:53 and 20:05 | [Runs 37149552725](https://github.com/skychaze/Aftertaste/actions/runs/37149552725) and [37150290165](https://github.com/skychaze/Aftertaste/actions/runs/37150290165), PR #30 | `AnalyticsScreenshotTest.dailyListeningScreenshot` failed with `AssertionError` at line 109. The earlier run had 87 tests with one failure; the later run had 89 tests with one failure. | The captured UI did not match the golden image. [PR #31](https://github.com/skychaze/Aftertaste/pull/31) found `canRepeat = true` in a fixture whose golden expected repeat disabled, then changed it to false. That alone did not clear the failure. |
| Oct 3 20:21 and 20:29 | [Run 37151264432](https://github.com/skychaze/Aftertaste/actions/runs/37151264432), PR #31; [run 37151719310](https://github.com/skychaze/Aftertaste/actions/runs/37151719310), push to `main` after PR #31 | The same `dailyListeningScreenshot` assertion failed. Each run reported 89 tests, one failure. | The fixture still depended on the current date because `DailyListeningView` supplies a live date by default. [PR #34](https://github.com/skychaze/Aftertaste/pull/34) pinned the screenshot date and playback state. It also corrected the Release Please query to use the actual `autorelease: pending` label. |
| Oct 3 20:30 | [Run 37151746698](https://github.com/skychaze/Aftertaste/actions/runs/37151746698), PR #33 Android CI | GitHub concluded `action_required`; there are no job logs. | This is a workflow approval/gating result, not a test failure. The workflow also dispatches Android CI explicitly because GitHub does not start follow-up workflows from the built-in token in this path. The dispatch run below did execute. |
| Oct 3 20:30 | [Run 37151751206](https://github.com/skychaze/Aftertaste/actions/runs/37151751206), dispatched Android CI for the release branch | `AnalyticsScreenshotTest.dailyListeningScreenshot` failed with `AssertionError` at line 109. It reported 89 tests, one failure. | Same screenshot fixture/date issue. The later [run 37152496516](https://github.com/skychaze/Aftertaste/actions/runs/37152496516) passed after PR #34 updated the fixture and release workflow. |
| Oct 3 20:42 | [Run 37152496359](https://github.com/skychaze/Aftertaste/actions/runs/37152496359), PR #33 Android CI on its updated head | GitHub records failure, but the run has no jobs or logs available. | Exact failure is unknown. The dispatched check for the same release PR revision, run 37152496516, passed. |
| Oct 4 06:47 | [Run 37183796224](https://github.com/skychaze/Aftertaste/actions/runs/37183796224), Release Please after PR #35 merged | Release Please reported success, but `check-release-pr`, `merge-release-pr`, and `release-apk` were all skipped. | The workflow created PR #36 and immediately ran a separate `gh pr list` query. That query did not see the new PR yet, leaving the PR number output empty. The job treated “no PR found” as success. This is a create-then-query race and a false-green workflow result. A later manual retry found the PR. |
| Oct 4 06:47 | [Run 37183814872](https://github.com/skychaze/Aftertaste/actions/runs/37183814872), PR #36 Android CI | GitHub later showed failure, but the run has no job details or logs. A separately dispatched check, [run 37183815661](https://github.com/skychaze/Aftertaste/actions/runs/37183815661), passed on the same release PR commit. | Exact cause of the PR-event run cannot be recovered. The explicit dispatch path did run and passed. |
| Oct 4 07:07 | [Run 37184860906](https://github.com/skychaze/Aftertaste/actions/runs/37184860906), manual Release Please retry | Its dispatched Android test failed at `:app:testDebugUnitTest`. `DailyListeningViewTest.goal editing is collapsed until requested and the launch action opens music` reported `ViewRootImpl$CalledFromWrongThreadException` and `ArrayIndexOutOfBoundsException` in Compose `SlotTable`. Merge and APK jobs were skipped because the check failed. | The failure points to a Compose/Robolectric UI-thread or test-isolation problem. The focused test and full unit suite both passed locally. The next workflow retry passed, so it was not reproduced locally and appears intermittent or environment-dependent. Root cause remains unproven. |

## Changes that resolved earlier issues

- [PR #27](https://github.com/skychaze/Aftertaste/pull/27) moved Last.fm secret setup after unit and screenshot checks, fixing release-only UI differences in screenshot tests.
- [PR #29](https://github.com/skychaze/Aftertaste/pull/29) made release-PR merge and release creation part of the same workflow run, accounting for GitHub's built-in token behavior.
- [PR #31](https://github.com/skychaze/Aftertaste/pull/31) corrected the repeat state in the analytics screenshot fixture. The subsequent CI failure showed the fix was incomplete.
- [PR #34](https://github.com/skychaze/Aftertaste/pull/34) pinned the screenshot date and playback state, and corrected the pending-release label used by the automation.
- [PR #36](https://github.com/skychaze/Aftertaste/pull/36) is the generated v1.2.11 release PR. The successful retry tested its exact head before merging it.

## v1.2.11 final outcome

[Release Please run 37185656926](https://github.com/skychaze/Aftertaste/actions/runs/37185656926) completed successfully:

- Android CI on the release PR: passed.
- Automatic merge and tag/release creation: passed.
- Release Android tests, Roborazzi screenshot checks, and lint: passed.
- Signed release APK build: passed.
- APK package and version identity check: passed.
- APK upload and GitHub release publication: passed.

[PR #36](https://github.com/skychaze/Aftertaste/pull/36) merged at 2026-10-04 07:26 UTC. [v1.2.11](https://github.com/skychaze/Aftertaste/releases/tag/v1.2.11) was published at 07:30 UTC with `aftertaste-v1.2.11-1002011.apk` (16,871,557 bytes). The latest release is no longer blocked.

## Recommended follow-up for the workflow owner

1. Remove the release-PR discovery race. Use the PR number and head SHA returned directly by Release Please when it creates or updates a PR. If discovery must use the GitHub API, retry briefly and fail the workflow clearly when the PR is still absent. Do not let an empty PR result make the overall release workflow green while all release jobs skip.
2. Investigate the Compose/Robolectric exception in `DailyListeningViewTest`. Re-run that test and the whole suite repeatedly on the same Linux CI image, check Robolectric/Compose UI-thread setup, and check whether tests share process or Compose state. The current evidence is not enough to name a confirmed source-level bug.
3. Preserve deterministic screenshot inputs. The live date and playback capability state both affected the analytics golden. Keep date, locale, and playback-control state explicit in screenshot fixtures.
4. Improve release PR check reporting. The PR-event checks marked `action_required` or `failure` without logs while dispatched checks provided the usable result. Make the dispatched check visible as the authoritative PR check and avoid leaving confusing empty check runs on bot-created release PRs.
5. Keep run logs or test reports available long enough to diagnose failed PR runs. Three failed PR-event runs in this audit had no job details or logs when inspected: 37144413663, 37152496359, and 37183814872.

## Scope and evidence limits

The audit began at PR #23's merge on 2026-10-03 11:07 UTC and ended after the v1.2.11 release was published on 2026-10-04 07:30 UTC. GitHub Actions run records, retained failed logs, PR descriptions and diffs, release metadata, and the local unit-test run were used. Some Actions records no longer expose logs; those items are marked unknown rather than attributed to a guessed cause. This is an audit of PR and release workflow failures, not a claim that every historical repository check before PR #23 was inspected.
