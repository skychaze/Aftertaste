# Insights tab

Year summary with active days, peak month, average minutes per active day, full-day listening equivalent, listener milestones, and a consecutive-day streak. The old 12-month histogram and month drilldown are intentionally not shown. A "Load sample data" button previews an empty selected year.

## Sub-features

- Year selector with large touch targets
- Year total, peak month, active days, average minutes per active day, and full-day equivalent
- Listener milestones at 5, 25, 50, 100, and 250 hours
- Consecutive-day streak in the year summary
- Sample data seeder for an empty selected year

## How to get to it (user POV)

Tap `content-desc` "Insights" in the bottom navigation. On a fresh install the current year is empty; the seed button sits in the view and may require scrolling. The year selector only includes the current year plus years already present in the database.

## Driving it with adb

1. Switch to the tab and capture `yearly-empty.png`. A fresh install should show zero hours and zero active days.
2. Locate "Seed Sample Data" with a fresh hierarchy dump, record the database row counts, and tap it once.
3. Pull the Room database and verify that `daily_stats` and `playback_sessions` contain rows for the selected year.
4. Compare the displayed total, active days, peak month, and average minutes per active day with grouped `daily_stats` queries. The average denominator is the number of active days, not all calendar days.
5. Verify the five milestone thresholds and compare each progress bar with cumulative `daily_stats.totalPlayTimeSeconds`.
6. Compare the displayed streak with consecutive positive dates counted backward from the device's current date. It is not scoped to the selected year.
7. In an empty selected year, tap the seed action once. Confirm it disappears after the year has data and that a rapid double tap creates one seed set. A later seed attempt is ignored when that year already has daily stats or sessions.
8. Select another year only when it appears in the selector. A fresh install cannot reach an arbitrary empty past year through the UI; when a past year is present, its totals and seed guard remain independent of today's data.

## Gotchas

- Seed buttons write Room rows directly. Restart the app before judging today's Daily or last-seven-day values after seeding.
- The seeder targets the selected year only when that year has no daily stats or sessions. It refuses to overwrite an existing year, and a second click does not double rows.
- The displayed streak always starts at the device's current date, even while a past year is selected.
- Peak month uses a compact hours-and-minutes label. The daily average uses active days.
- Database checks use `daily_stats(date, year, month, day, dayOfWeek, totalPlayTimeSeconds, sessionCount, ...)` and `playback_sessions(..., durationSeconds, title, artist, album, genre, playCount, sourcePackage)`.
