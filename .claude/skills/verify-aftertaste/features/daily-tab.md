# Daily tab

Today's listening: total time, session count, a configurable daily goal in minutes with a progress ring, and the track feed grouped by normalized title+artist with per-track seconds and play counts. The currently playing track shows as a live row updating every second. Repeats fold into one row via `playCount` (rendered `2x`, `3x`), not duplicate rows.

## Sub-features

- Today total and session count (live; "TODAY'S LISTENING TIME", big "01h 17m" style timer, "PAUSED"/playing chip)
- Daily goal: "Daily Goal: 60m" label + "N% achieved" + preset chips 30m / 60m / 90m / 120m (progress ring)
- Track feed "TODAY'S TRACK FEED": grouped by normalized title+artist, play count badges, per-row genre + timestamp
- Live row for the playing track, per-second updates

## How to get to it (user POV)

It is the first tab: tap `content-desc` "Daily" in the top tab row (default selection on cold start).

## Driving it with adb

1. Switch to the tab, screencap (`daily-tab-<step>.png`).
2. Empty-state math matches the DB: pull the DB, `SELECT SUM(totalPlayTimeSeconds) FROM daily_stats WHERE date='<today>'` must equal the minutes shown.
3. Goal (VERIFIED): tap a preset chip (30m/60m/90m/120m; 90m set: label "Daily Goal: 90m", "0% achieved" on an empty today). Persistence across `force-stop` + relaunch: VERIFIED, the 90m selection survived. In the same restart, "0% -> 85% achieved" showed the engine rehydrating today's seeded 77m row, consistent with the big timer showing `todayTotalSeconds`.
4. Live tracking (VERIFIED, real signed-in playback): timer ticks per second (00m 36s -> 01m 21s -> 01m 52s); the live feed row shows the playing track; "N tracks played" equals DB `sessionCount` (3 while the 3rd session flushed); feed row totals match DB session seconds exactly (2m 19s = 139s, 6s row for the kept short track).
5. Pause (VERIFIED): `dispatch pause` -> "PAUSED" chip, timer frozen, DB today flush stops at the paused value.
6. Side effect: DB `daily_stats` row for today increments in 5-second flush steps, never per second; today grew 4620 (seed) -> 4710 (+90 real) -> 4732 (+112 flush) -> 4765 (+33 skip test).

## Gotchas

- Seeding is invisible to the live engine: seed buttons write Room rows directly, `todayTotalSeconds` stays at its boot value. Daily shows 0 for today until an app restart, even though `daily_stats` has today's minutes (verified: 0m after seeding, 1h 17m after restart). Always restart after seeding before judging today-facing UI.
- Rehydration on restart is verified: the engine loads today's `daily_stats` row (`loadTodayStatFromDb`).
- Sessions under 5 seconds are discarded and rolled back out of the daily total; a quick skip can make the on-screen total go down. Correct behavior.
- Feed grouping normalizes title+artist; two entries with cosmetic differences may merge. Check `MusicTrackerRepository` normalization rules before filing a bug.
- The live row updates every second but the DB only every 5s; the screen can lead the DB by up to 5 seconds.
