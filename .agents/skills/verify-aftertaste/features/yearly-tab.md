# Yearly tab

Year summary with active days, peak month, daily average, listener milestones, and a consecutive-day streak. The old 12-month histogram and month drilldown are intentionally not shown. A "Seed Sample Data" button loads a rich sample catalog for the selected year.

## Sub-features

- Year selector with the selected year chip
- Year total, peak month, active days, daily average, and full-day equivalent
- Listener milestones at 5, 25, 50, 100, and 250 hours
- Consecutive-day streak below the year summary
- Sample data seeder for an empty selected year

## How to get to it (user POV)

Tap `content-desc` "Yearly" in the top tab row. On a fresh install it is empty; the seed button sits in the view and may require scrolling.

## Driving it with adb

1. Switch to the tab and capture `yearly-empty.png`. A fresh install should show zero hours and zero active days.
2. Locate "Seed Sample Data" with a fresh hierarchy dump, record the database row counts, and tap it once.
3. Pull the Room database and verify that `daily_stats` and `playback_sessions` contain rows for the selected year.
4. Compare the displayed total, active days, peak month, and daily average with grouped `daily_stats` queries.
5. Verify the five milestone thresholds and compare each progress bar with cumulative `daily_stats.totalPlayTimeSeconds`.
6. Compare the displayed streak with the consecutive dates that have positive daily totals.
7. Confirm the seed button disappears after the selected year has data and that a rapid double tap creates one seed set.
8. Select a past year. Its empty state should remain independent of today's data, and seeding should target that selected year.

## Gotchas

- Seed buttons write Room rows directly. Restart the app before judging today's Daily or last-seven-day values after seeding.
- The seeder targets the selected year. Daily stats for today should not change when seeding a past year.
- Database checks use `daily_stats(date, year, month, day, dayOfWeek, totalPlayTimeSeconds, sessionCount, ...)` and `playback_sessions(..., durationSeconds, title, artist, album, genre, playCount, sourcePackage)`.
