# Now playing card

The live card at the top of the main screen. Shows title, artist, album, genre, artwork, a running session timer, and a position/duration timeline while YouTube Music plays. One tap button opens YT Music (web fallback to music.youtube.com). A permission banner appears when notification listener access is missing.

## Sub-features

- Live session timer and estimated playback position timeline (driven by `MediaController` playback state + `AudioManager.isMusicActive`, not foreground time)
- "Launch YouTube Music" button with YT Music app intent, web fallback
- Notification listener permission banner ("Permission Alert" icon, "Enable" button)
- Empty state: "PLAYBACK PAUSED" / "Ready to track" / "No music playing" / "Waiting for YouTube Music"
- Placeholder rejection: metadata like "No music playing", "Unknown Track", bare "YouTube Music" never becomes a session

## How to get to it (user POV)

Cold-start the app. The card is the first thing under the header, no navigation needed. A first-run info dialog ("How YT Track Works" / "Got It") may cover it; dismiss with "Got It" (not observed on this AVD's cold start, the dialog is conditional).

## Driving it with adb

1. Launch and dismiss the info dialog if present.
2. Empty state check (verified): card reads "PLAYBACK PAUSED", "Ready to track", "Open YouTube Music to start"; the Daily tab big timer shows "00h 00m 00s" and a "PAUSED" chip.
3. Banner: with listener access denied, the card shows "Notification Access Required" + "Enable" (opens notification listener settings). Grant via adb, then restart the app: the banner clears only on re-evaluation, not live (verified).
4. Playback positive path (VERIFIED end-to-end, 2026-09-05, signed-in account): start a track via "Feeling lucky" shuffle, wait 15s, screencap twice ~25s apart. VERIFIED: live timer 00m 36s -> 01m 21s (+45s elapsed) -> 01m 52s frozen at pause; DB `daily_stats` today grew 4620s (seed) -> 4710s -> 4732s; a `playback_sessions` row appeared for the real track (FINE SHYT, `sourcePackage=com.google.android.apps.youtube.music`, durationSeconds 90 -> 112 -> 139, playCount 1); on-screen "N tracks played" equals DB `sessionCount`; feed per-row totals match DB session seconds exactly (2m 19s = 139s).
5. Pause (VERIFIED): `adb shell cmd media_session dispatch pause` freezes the engine: "PAUSED" chip, timer frozen at 112s, DB flush stops at the paused value (today 4732s). Resume with `dispatch play`. Track change with `dispatch next` (NOT "skip-to-next", which errors).
6. Track-change guards (VERIFIED): `dispatch play` then `dispatch next` x3 with ~4s gaps. The two ~4s tracks were DISCARDED (no rows, seconds rolled back from today); the ~6s track was KEPT (durationSeconds=6 >= 5s threshold) — the >=5s threshold verified both ways.
7. Button: tap `content-desc` "Launch YouTube Music". VERIFIED: opens the YT Music app directly on a signed-in AVD (`MusicActivity` on top).
8. DB side effect: pull the Room DB (see SKILL.md) and check a `playback_sessions` row with `durationSeconds >= 15` for the played track; `daily_stats.todayPlayTimeSeconds` for today grows in 5s flush steps.

Negative path worth proving: YouTube main app video, sub-5-second track skip, placeholder metadata. No new session row; today total unchanged.

JVM fallback: `NowPlayingCard` is covered by `app/src/test/java/com/example/GreetingScreenshotTest.kt` (Roborazzi baseline `src/test/screenshots/greeting.png`). Run `:app:verifyRoborazziDebug`.

## Gotchas

- Without listener access granted, nothing ever tracks; the banner is the tell. Grant it before any positive playback proof, then restart the app.
- The DB flushes every 5 seconds and discards sessions under 5 seconds; quick taps produce no rows by design. Wait 15s+ before expecting data.
- On a bare emulator with no media session, "nothing playing" is correct, not a bug. Capture it as a negative result.
- `dispatch play` alone does not create a real session; only actual audio playback counts.
- YT Music on this AVD needs its own notification permission (POST_NOTIFICATIONS) for media notifications; if the request was wrongly denied, `pm grant com.google.android.apps.youtube.music android.permission.POST_NOTIFICATIONS` (the app does not re-request on relaunch, verified).
- `pm clear` on AfterTaste ALSO revokes the notification listener grant (`enabled_notification_listeners` no longer lists the package), so the banner returns. Re-grant with `cmd notification allow_listener ...` and restart the app (verified).
- The seeder's sessions carry `sourcePackage=com.google.android.apps.youtube.music`, so a `sourcePackage LIKE '%youtube%music%'` query cannot distinguish seeded from real sessions; distinguish them by exact track totals from the seed catalog (e.g. Levitating 2772s, Do I Wanna Know? 1848s).
