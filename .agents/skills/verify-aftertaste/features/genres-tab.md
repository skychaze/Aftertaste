# Genres tab

Genre donut chart of listening time, with scope switcher (Month / Year / All, rendered labels, "All" = All Records), per-genre rows, and an active-genre panel with top artists and unique tracks. A donut-slice or genre-row tap changes the active genre. Populated scopes show the panel immediately. Empty state offers "Load Sample Genre Data". A Spotify status bar ("Spotify API: Not Configured (Tap to Setup)") with a configure gear.

## Sub-features

- Genre donut chart (share of listening time per genre; center label shows the active genre: "Hip-Hop / Rap 21.1% 3 Days 5 Hours")
- Scope switcher: Month / Year / All (All Records)
- Per-genre rows: name, floored hour label, exact percentage
- Active-genre panel: "N unique tracks • total", Top Artists line, filtered non-repeating track list with Close button
- Sample data seeding from the empty state or the Yearly tab; the repository skips a seed when the selected year already has data

## How to get to it (user POV)

Tap `content-desc` "Genres" in the bottom navigation. Fresh install shows "No Genre History Yet" with "Load Sample Genre Data". After seeding from Insights, the tab is populated and the empty-state button is gone.

## Driving it with adb

1. Switch to the tab, screencap (`genres-empty.png`). Empty state text: "No Genre History Yet".
2. Seed (if empty): tap `text` "Load Sample Genre Data". Screencap after. If populated already, skip; seeding lives in the Yearly tab.
3. Scope Month (verified): per-genre percentages match DB `WHERE month=<now>` shares to one decimal place (28.8/24.6/15.5/13.0/10.3/7.7 on seeded data); hour labels floor whole hours: Rock at 48 min renders "0 Hours", Electronic at 1.91h renders "1 Hour". "0 Hours at 10.3%" is correct math, not a bug.
4. Scope Year / All (verified): Year matches DB `WHERE year=2026` shares (Hip-Hop 21.1%, Pop/Electronic 16.9% tie order may swap among exact ties); All equals Year when all data is in one year.
5. Active genre panel (verified): on a populated scope, confirm the panel is already visible. Tap a donut ring slice or a genre row to select another genre. The selected genre card shows "4 unique tracks • 3 Days 5 Hours" + "Top Artists" matching DB `COUNT(DISTINCT title)` and `GROUP BY genre` totals (Hip-Hop: 4 titles, 77h), and the filtered track list has a Close button.
6. Cross-tab consistency: the genre of a track in the Daily feed matches its genre here (same classification pipeline, `MusicGenreResolver` / `GenreClassifier`).

## Gotchas

- Genre classification may hit Spotify, iTunes, and MusicBrainz resolvers over the network for unknown tracks (`MusicGenreResolver`); offline, the local classifier still assigns a fallback genre, usually `Pop`.
- Seeding from the Genres tab and the Yearly tab hits the same guarded `seedSampleData()`. Seeding once is enough for both; later attempts for a year that already has daily stats or sessions are ignored.
- "All Records" can disagree with This Year if seeded data spans a different year than the selector's default.
- Cosmetic nit (verified): the selected-genre card's total and Top Artists text overlap ("3 Days 5 HoursPost Malone, Tra…"). Capture as cosmetic, do not block on it.
- Per-genre hour labels floor; compare percentages against the DB, not the hour labels.
- The filtered track panel is driven by the active genre, which defaults to the first genre in every populated scope. Closing it clears the explicit selection, then the first genre becomes active again, so the panel reappears.
