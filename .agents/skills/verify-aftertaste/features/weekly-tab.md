# Last seven-day record

The last seven calendar days appear in a horizontally scrollable minutes histogram. Tap a day to open its unique tracks. Today uses the live engine counter, while older days use `daily_stats`.

## Sub-features

- Last-seven-day listening total and average per day
- Horizontally scrollable day histogram
- Per-day drilldown with normalized unique tracks and play counts
- Carried sessions use their stored per-date contribution when available

## How to get to it

Tap the top tab with content description `Last seven-day record`.

## Driving it with adb

1. Switch to the tab and capture `last-seven-day-record.png`.
2. Cross-check each bar against the last seven `daily_stats` rows. Today is expected to use the live counter.
3. Swipe the histogram horizontally, then tap a day. The drilldown must contain only tracks with contribution on that day.
4. Compare the average with the seven displayed bar seconds, using floored minutes.

## Notes

- Dates use the device timezone.
- A day with no listening remains visible as a zero bar.
- The database flushes active seconds in five-second batches, so the live bar can lead the stored value briefly.
