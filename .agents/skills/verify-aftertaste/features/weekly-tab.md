# Weekly tab

Last 7 days as a per-day minutes histogram, plus the week average. Tapping a bar drills into that day's unique tracks.

## Sub-features

- Weekly total ("WEEKLY LISTENING TIME", "6h 28m" style big timer)
- Week average ("Avg: Xm / day" chip)
- 7-day histogram (minutes per day, day label + day number under each bar)
- Per-day drill down to unique tracks

## How to get to it (user POV)

Tap `content-desc` "Weekly" in the top tab row.

## Driving it with adb

1. Switch to the tab, screencap (`weekly-tab-<step>.png`).
2. Cross-check the histogram: pull the DB and compare the last 7 days' `daily_stats` minutes against each bar label. Bar x-positions map left-to-right oldest-to-newest; day labels can wrap to two lines ("Tue" at a lower y than "Sun"), so group labels by bar x-position, not by y.
3. Today rule: the histogram's today bar uses the engine's live counter, not the DB (`MainViewModel.kt:475`). With a stale counter (app booted before today's rows existed), today renders "0m" and the weekly total excludes today's DB minutes exactly (verified: DB 7-day sum 465m, UI 6h 28m = 388m = 465-77). After a restart the rehydrated counter fixes it.
4. Drill down: tap one bar. The unique-track list for that day appears; rows must match the daily feed for that date (same grouping, no play-count duplicates).
5. Average: recompute from the DB pull and compare with the on-screen chip.

## Gotchas

- The histogram uses local device dates; check `adb shell getprop persist.sys.timezone` before assuming a mismatch is a bug.
- The "last 7 days" window is today-anchored (`Calendar.add(DAY_OF_YEAR, -i)` for i in 6..0); a day with no listening shows "0m", not an empty slot.
- With only seeded data, bars concentrate on the seed dates; empty days showing zero is expected.
- Drill-down lists unique tracks, so a track played 5 times shows once with its total.
- Rounding: bar labels floor minutes to "2h"/"43m" style (134m renders "2h"); compare against `seconds/60` floored, not exact.
