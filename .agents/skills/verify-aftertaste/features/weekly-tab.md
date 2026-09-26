# History tab

History shows the selected 7, 30, or 90 calendar days with the period total first, daily average, and horizontally scrollable daily bars. Tap a day to open its tracks in a panel below the chart; the panel title is the pretty date (e.g. "Sun 6 Sep") and its rows are not genre-editable. Daily totals come from `daily_stats`; track details come from date-scoped session queries.

## Sub-features

- Listening total and average per day for the selected range
- Segmented range control (7 / 30 / 90 days); horizontal day bars, with missing dates shown as zero
- Per-day drilldown panel with normalized unique tracks and play counts
- Carried sessions use their stored per-date contribution when available

## How to get to it

Tap `content-desc` "History" in the bottom navigation. The default range is 7 days.

## Driving it with adb

1. Switch to the tab and capture `last-seven-day-record.png`.
2. Read each chart day and cross-check it against `daily_stats`, treating missing rows as zero.
3. Change the range to 30 or 90 days and confirm the chart contains the selected number of calendar dates. Swipe horizontally to reach later dates.
4. Tap a day. Its track detail must contain only tracks with a contribution on that date.
5. Compare the average with the displayed daily totals, using floored minutes.

## Notes

- Dates use the device timezone.
- A day with no listening remains visible as a zero bar.
- The database flushes active seconds in five-second batches, so a displayed daily total can trail the live timer briefly.
- An open day detail observes database updates and refreshes as session records change.
