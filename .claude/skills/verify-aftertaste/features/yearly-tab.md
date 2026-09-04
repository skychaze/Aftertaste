# Yearly tab

12-month histogram with hours, active days, peak month, year total, and daily average. Listener milestones at 5/25/50/100/250 hours, a consecutive-day streak, and a month detail view (day grid, week breakdown, weekday vs weekend averages). A "Seed Sample Data" button loads a rich sample catalog for the selected year.

## Sub-features

- Year selector ("Year Analytics" + year chip, 2026 verified)
- Year summary ("2026 LISTENING TIME"): header total two ways ("15 Days 5 Hours" at 24h/day + "365h 55m"), peak month chip ("Peak: Jan (46h)"), stat cards: Active Days, Daily Avg, Full Days
- 12-month histogram ("YEARLY HISTOGRAM (12 MONTHS)", per-month hour labels, "Tap any month to view unique tracks played"; future months render as dashed "-" placeholders)
- Listener milestones ("YEARLY LISTENING MILESTONES" at 5/25/50/100/250 hours; this is the section whose code colors say "Streak" — the milestone progress chip, not a streak number)
- `currentStreakDays` (repository computes a backward walk from today, 221 days on seeded data) has NO renderer in any component. Dead state field: prove compute vs DB math but expect no on-screen value; flag as a finding.
- Month detail (AnimatedVisibility under the histogram: day grid, per-track ranked list, Top Artists)
- Sample data seeder (empty-state card only, see step 7)

## How to get to it (user POV)

Tap `content-desc` "Yearly" in the top tab row. On a fresh install it is empty; the seed button sits in the view (scroll down to reach it; the view is tall).

## Driving it with adb

1. Switch to the tab, screencap (`yearly-empty.png`). Fresh install: "0 Hours", "0 Days" Active, "0 min" Daily Avg, "0d" Full Days (verified).
2. Seed: locate "Seed Sample Data" by dump (it may need scrolling), record the DB row count BEFORE, tap. Screencap after.
3. Side effect proof (the important one): pull the DB. Verified after one seed: `playback_sessions` 458 rows, `daily_stats` 229 rows spanning 2026-01-01..2026-09-05.
4. Stats sanity (verified): UI 365h 55m = DB 365.92h; 229 Active Days = DB distinct dates; 95 min Daily Avg = DB 95.9; "Peak: Jan (46h)" = DB `GROUP BY year, month ORDER BY SUM(durationSeconds) DESC LIMIT 1`; "15 Days 5 Hours" = total at 24h/day (365.92h = 15d 5.92h).
5. Milestones (VERIFIED at 365.92h total): all five chips (5h Bronze, 25h Silver, 50h Gold, 100h Platinum, 250h Diamond) render full gold bars and filled stars; at a total under a threshold that chip's bar is partial. Progress bars recompute from cumulative `SUM(totalPlayTimeSeconds)` vs thresholds. Histogram hour labels floor: September shows "7h" for the DB's exact 7.75h (27900s).
6. Month detail (VERIFIED): tap a histogram bar (locate from a fresh screencap; the 540x1200 layout puts bars at y=415-580). Selected bar turns blue; "Sep 2026 / Unique Tracks Played (7 Hours) • 10 unique tracks" renders under the histogram with a ranked per-track list — every total matched DB seconds EXACTLY (Levels 90m 36s = 5436s, Time 80m 24s = 4824s, Blinding Lights 60m 24s = 3624s, Nuvole Bianche 53m 36s = 3216s), Top Artists matched `GROUP BY genre` (The Weeknd), and 10 unique tracks = DB `COUNT(*) WHERE month=9`. Tap the bar again (or the X) to close.
7. Double-seed edge case (verified): the seed card renders only while `yearTotalSeconds == 0L` (`YearlyAnalyticsView.kt:578`), so once seeded the button vanishes. A rapid double-tap fired ONE seed in testing; seeding is deterministic (two fresh seeds produced identical 458/229/365.92h). An early DB pull catches the seed mid-write; wait ~30s or for the UI stats to settle before counting. Seed from a `pm clear`-ed app state.
8. Year selector: tap the year chip, select a past year. Expect zeros (no DB rows) and the seeder targeting the newly selected year, not today.

## Gotchas

- Seeding is DETERMINISTIC: two fresh seeds produced identical DB state (458 sessions / 229 daily / 365.92h, verified). An early DB pull can catch the seed mid-write (306/154 was partial), so wait for the UI stats to settle (or ~30s) before counting. The idempotency guarantee comes from the button gating (`yearTotalSeconds == 0L`), which unmounts the card faster than adb tap round-trips: a rapid double-tap fired one seed.
- The seeder targets the selected year. If the selector is on a past year, today's stats elsewhere (Daily tab) do not change; do not read that as a bug.
- Seeding is invisible to the live engine: restart the app after seeding before judging today-facing UI (Daily/Weekly).
- Weekday vs weekend averages split on device locale calendar; a mismatch claim needs the timezone/locale context from the doctor output.
- DB schema for cross-checks: `daily_stats(date, year, month, day, dayOfWeek, totalPlayTimeSeconds, sessionCount, ...)`; `playback_sessions(..., durationSeconds, title, artist, album, genre, playCount, sourcePackage)`.
