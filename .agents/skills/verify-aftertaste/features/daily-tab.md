# Today tab

Today opens with the tab title, live status (Tracking / Paused / Waiting), date, one large listening total, and daily-goal progress ("N minutes to your daily goal"). Goal presets stay hidden until "Edit goal" is tapped. A compact live player sits between the summary and today's grouped track list. Track rows are slim divider-separated lines: artwork, title, artist, duration, and play count (`2 plays`). Repeated plays fold into one row via `playCount`.

## Sub-features

- Today total and grouped track count, with a compact elapsed-time total
- Daily goal progress with hidden 30m / 60m / 90m / 120m presets and a linear progress bar
- Grouped track feed with play counts; live row for the playing track with per-second session time
- No genre chips or timestamps in the feed; more history fits without scrolling

## How to get to it (user POV)

Tap `content-desc` "Today" in the bottom navigation. It is selected on cold start.

## Verification notes

1. Switch to the tab, screencap (`daily-tab-<step>.png`).
2. Empty-state math matches the DB after a restart: pull the DB, `SELECT SUM(totalPlayTimeSeconds) FROM daily_stats WHERE date='<today>'` must equal the minutes shown. During playback, the live engine counter can lead the stored row until the next flush.
3. Goal: tap "Edit goal", choose a preset (30m/60m/90m/120m), and confirm the preset row closes. The goal preference persists across relaunch.
4. Live tracking (VERIFIED, real signed-in playback): timer ticks per second (00m 36s -> 01m 21s -> 01m 52s); the live feed row shows the playing track; feed row totals match DB session seconds exactly (2m 19s = 139s, 6s row for the kept short track). Compare the displayed "N tracks played" with the number of normalized title+artist groups, and compare `daily_stats.sessionCount` separately.
5. Pause (VERIFIED): `dispatch pause` -> "PAUSED" chip, timer frozen, DB today flush stops at the paused value.
6. Side effect: DB `daily_stats` row for today increments in 5-second flush steps, never per second; today grew 4620 (seed) -> 4710 (+90 real) -> 4732 (+112 flush) -> 4765 (+33 skip test).

## Gotchas

- Seeding is invisible to the live engine: seed buttons write Room rows directly, `todayTotalSeconds` stays at its boot value. Daily shows 0 for today until an app restart, even though `daily_stats` has today's minutes (verified: 0m after seeding, 1h 17m after restart). Always restart after seeding before judging today-facing UI.
- Rehydration on restart is verified: the engine loads today's `daily_stats` row (`loadTodayStatFromDb`).
- Sessions under 5 seconds are discarded and rolled back out of the daily total; a quick skip can make the on-screen total go down. Correct behavior.
- Feed grouping normalizes title+artist; two entries with cosmetic differences may merge. Check `MusicTrackerRepository` normalization rules before filing a bug.
- The live row updates every second but the DB only every 5s; the screen can lead the DB by up to 5 seconds.
- The feed count is the number of normalized title+artist groups, not the Room session count. Repeated sessions collapse into one row and show a `2x` or `3x` badge.
