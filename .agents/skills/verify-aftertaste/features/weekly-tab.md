# History tab

History shows the selected 7, 30, or 90 calendar days with a period total, daily average, and horizontally scrollable daily chart. Tap a day to open its tracks below the chart. Daily totals come from `daily_stats`; track details come from date-scoped session queries.

## Sub-features

- Listening total and average per day for the selected range
- Horizontal day chart, with missing dates shown as zero
- Per-day drilldown with normalized unique tracks and play counts
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
