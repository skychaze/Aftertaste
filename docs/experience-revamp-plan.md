# AfterTaste experience revamp

## Purpose

AfterTaste is a private listening journal. Its screens should answer what played, what keeps returning, and how musical taste develops over months.

## Design direction

A colourful record journal with confident typography, a soft lilac-to-peach background gradient and real album artwork. Use an indigo navigation panel for contrast. A record with an offset cut is the app's identifying mark. Keep charts quiet and make songs easy to recognise and inspect.

Palette: paper `#F7F7FB`, white `#FFFFFF`, ink `#24243D`, cobalt `#4855C8`, periwinkle `#DDE2FF`, peach `#FFC9A5`, mint `#BCDCD0`. Use genre colour to distinguish listening categories. Only the launcher monochrome layer uses a single colour. Bundle Manrope for consistent offline typography. Use 32sp page titles, 16sp song names and 14sp supporting text. Keep content left aligned, controls at least 48dp, and room for Android font scaling.

```
Today                  History
Date / listening time  Search song or artist
Artwork / song         Period / recent or most played
Previous pause next    Track rows / listening days
Goal / today's tracks  Tap song for its details

Insights               Genres
Compact year picker    1 month / 3 months / 6 months
Monthly rhythm         Taste summary / top 3 genres
Weekday habits         Full statistics on demand
Listening consistency  Most played / favourite artists
```

Keep the background gradient subtle enough for clear text. Avoid identical cards around every section and meaningless badges. Use motion for playback progress, artwork transitions and opening details. Preserve Android's reduced animation settings.

## Implementation

1. Apply shared palette, bundled type and a vector adaptive launcher icon with a separate transparent monochrome layer.
2. Expose Android media session capabilities and guarded transport actions. Preserve paused controllers so resuming works after returning to AfterTaste.
3. Add bounded searchable history aggregates, sorting and track inspection. Keep daily totals and day drilldowns available.
4. Keep Insights focused on active-day averages, listening streaks and monthly and weekday patterns. Keep song and artist rankings in Genres to avoid repetition.
5. Add rolling one, three and six month genre periods. Keep calendar-year and lifetime views available. Show a factual taste summary, genre listening share, artist rankings and most-played songs. Preserve exact midnight boundary accounting.
6. Reproduce cache eviction with a focused artwork test. Move saved art to durable app storage, import readable legacy artwork and recover unavailable references when a song becomes visible. Always render a designed fallback.
7. Run relevant JVM and UI tests, build, lint, and screenshot verification. Inspect populated and empty screens on the shared Android emulator and audit labels, contrast, target sizes, overflow and navigation.

## Acceptance

- Playback actions affect the attached player, without pretending to play historical songs.
- History search finds titles and artists within the chosen period.
- Six month taste rankings use real listening totals and play counts, with clear labels for the metric.
- Unknown genre coverage stays visible and does not become an invented preference.
- Cached music artwork survives cache cleanup and stale paths do not leave empty image tiles.
- Existing data and genre corrections remain intact.

## Verification

Completed on 3 October 2026.

- Debug APK built successfully with JDK 21 and Android SDK 36.
- All 80 JVM and Compose tests passed, including search and ranking queries, period boundaries, playback capabilities, artwork persistence, and month selection after asynchronous loading.
- Android lint completed with 0 errors and 41 warnings. The compiler warnings are in existing update code.
- Regenerated the eight existing Roborazzi baselines through the recording task, inspected the analytics images, and passed screenshot verification.
- Checked the populated app on the shared Android emulator. Verified actual YouTube Music playback commands, history search, the six-month taste period, full genre statistics, and font scaling at 150 percent.
- Enabled Android themed icons and verified the new record mark remains visible. The Pixel launcher keeps its app drawer icons colourful; OEM drawer theming uses the supplied transparent monochrome layer.
- Audited Compose button roles and labels, chart descriptions, touch targets, bounded lazy lists, long song text, safe areas and reduced animation settings.

Genre track drilldowns use a lazy list capped at 420dp with a Close button. They show the top 100 songs by listening time for the selected period. History loads 50 songs initially and supports loading more.
