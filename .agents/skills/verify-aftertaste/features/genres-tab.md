# Genres tab

Genre donut chart of listening time with "This month", "This year", and "All time" scopes. Tapping a genre opens that genre's track list. Compact rows show minutes and share; the chart center shows the selected genre's exact total in a short format. Empty state offers "Load Sample Genre Data".

## Sub-features

- Genre donut chart (share of listening time per genre; center label shows the active genre: "Hip-Hop / Rap 21.1% 3 Days 5 Hours")
- Scope switcher: This month / This year / All time
- Per-genre rows: name, compact duration, exact percentage
- Selected-genre panel: filtered unique track list with a Close button
- Sample data seeding from the empty state or the Yearly tab; the repository skips a seed when the selected year already has data

## How to get to it (user POV)

Tap `content-desc` "Genres" in the bottom navigation. Fresh install shows "No Genre History Yet" with "Load Sample Genre Data". After seeding from Insights, the tab is populated and the empty-state button is gone.

## Driving it with adb

1. Switch to the tab, screencap (`genres-empty.png`). Empty state text: "No Genre History Yet".
2. Seed (if empty): tap `text` "Load Sample Genre Data". Screencap after. If populated already, skip; seeding lives in the Yearly tab.
3. Scope Month (verified): per-genre percentages match DB `WHERE month=<now>` shares to one decimal place (28.8/24.6/15.5/13.0/10.3/7.7 on seeded data); hour labels floor whole hours: Rock at 48 min renders "0 Hours", Electronic at 1.91h renders "1 Hour". "0 Hours at 10.3%" is correct math, not a bug.
4. Scope Year / All (verified): Year matches DB `WHERE year=2026` shares (Hip-Hop 21.1%, Pop/Electronic 16.9% tie order may swap among exact ties); All equals Year when all data is in one year.
5. Selected genre: tap a donut slice or genre row. Confirm the panel heading and track list both match the selected genre, then close the panel and select another genre.
6. Cross-tab consistency: the genre of a track in the Daily feed matches its genre here (same classification pipeline, `MusicGenreResolver` / `GenreClassifier`).

## Gotchas

- Genre classification may hit Spotify, iTunes, and MusicBrainz resolvers over the network for unknown tracks (`MusicGenreResolver`); offline, the local classifier still assigns a fallback genre, usually `Pop`.
- Seeding from the Genres tab and the Yearly tab hits the same guarded `seedSampleData()`. Seeding once is enough for both; later attempts for a year that already has daily stats or sessions are ignored.
- "All Records" can disagree with This Year if seeded data spans a different year than the selector's default.
- The selected-genre panel follows the explicit selection. When nothing is selected, no track panel appears.
- Duration labels round down to whole minutes; compare percentages against the stored totals when checking exact values.
