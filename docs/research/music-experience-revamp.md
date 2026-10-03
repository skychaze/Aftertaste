# AfterTaste music experience research

Research date: 2026-10-02

This note turns first-party patterns from music trackers and players into decisions for AfterTaste. The focus is attractive, creative presentation, searchable listening history, understandable insights, and a Genre view for 1, 3, and 6 months.

The product decisions below describe research recommendations. The [implementation plan](../experience-revamp-plan.md) records the chosen scope and completed checks.

## Product patterns

### Listening history should be a first-class destination

Last.fm frames its value around an accumulating listening profile, reports, charts, and the ability to revisit specific days. Its Android app description calls out Last.week and Last.year reports, personal artist, album, and track charts, and listening history ([Last.fm Track My Music](https://www.last.fm/about/trackmymusic), [Last.fm About](https://www.last.fm/about)).

stats.fm puts a period selector and listening totals beside top genres, tracks, artists, and albums. Public profile pages also show recent streams with a "show all" path. Its own product page emphasizes ordering top lists by playtime or stream count, and its support page says a complete history import unlocks full history, exact play counts, and charts ([stats.fm example profile](https://stats.fm/listener22), [stats.fm product page](https://www.stats.fm/), [stats.fm import details](https://support.stats.fm/docs/import/)).

Spotify's Library offers collection filters, search, sorting, and saved filter state. YouTube Music organizes its Library by content type, offers genre filters for songs, and sorts by recent activity, recently saved, or recently played ([Spotify sort and filter](https://support.spotify.com/cm-en/article/sort-and-filter/), [YouTube Music library](https://support.google.com/youtubemusic/answer/6313542?hl=en)).

Both players connect queue management directly to the active player: Spotify exposes an editable Play Queue from Now Playing, and YouTube Music lets listeners tune a custom mix and adjust the queue from Up Next ([Spotify Play Queue](https://support.spotify.com/us/article/play-queue/), [YouTube Music custom mix](https://support.google.com/youtubemusic/answer/15165061?hl=en)).

**Decision for AfterTaste:** Keep History as a main destination with search visible at the top. Search track, artist, and album names; let filters narrow by date and genre; support recent, most played, and most listened sorting. Retain the query and filters when a user opens a result and returns. Put a clear date and a compact daily summary above the matching tracks, and let a track open its individual listening sessions. This combines Last.fm's revisitable timeline and stats.fm's detailed event history with the familiar filter model of the players.

### Insights work best as ranked facts with a way to inspect them

Last.fm and stats.fm both present charted top items over a named period. stats.fm includes multiple measurements such as minutes and streams, while YouTube Music Recap adds a few human-readable dimensions such as top songs and artists, listening days for a top artist, and artists' countries of origin. YouTube Music's "On repeat" lists songs, artists, and playlists and refreshes daily ([stats.fm example profile](https://stats.fm/listener22), [YouTube Music Recap](https://support.google.com/youtubemusic/answer/11418178?co=GENIE.Platform%3DAndroid&hl=en_ZA), [YouTube Music top stats](https://support.google.com/youtubemusic/answer/13407991?hl=en)).

**Decision for AfterTaste:** Every insight should name its measurement and time window, then expose the contributing tracks. Prefer statements such as "Ambient was 28% of your listening time in the last 3 months" over an unexplained "Your sound is ambient." Include both listening time and play count where each tells a different story. Let a user tap a genre, artist, or track to see the source rows behind the total. For change claims, compare equal-length adjacent periods and show the values; do not imply a change from a single-period ranking.

### Genre needs the requested periods and a useful drilldown

stats.fm's profile layout puts Top genres, Top tracks, Top artists, and Top albums in the same selected period. Its support documentation is also a reminder that counts depend on the source data and counting rules: skipped tracks and very short streams can be excluded, and local files or podcasts may lack upstream metadata ([stats.fm example profile](https://stats.fm/listener22), [stats.fm import details](https://support.stats.fm/docs/import/)). YouTube Music similarly uses genre filters as a way to browse songs by mood, rather than presenting a taxonomy in isolation ([YouTube Music library](https://support.google.com/youtubemusic/answer/6313542?hl=en)).

**Decision for AfterTaste:** Make Genre a period-based taste report with explicit 1 month, 3 months, and 6 months controls. Show the top genres ranked by listening time, with a small share-of-total cue and play count as secondary context. Selecting a genre should reveal its top songs, also ranked by listening time, and an affordance into the matching history. Use the same period across the genre ranking and song list. Label missing or inferred genre metadata, and provide an "Unknown genre" group rather than hiding plays or presenting uncertain tags as fact.

### Creative visual treatment should serve the data

The products make personal listening legible through covers, top lists, period labels, and distinct recap cards. Their strongest reusable pattern is varied hierarchy around familiar facts, not decorative charts without a clear reading.

**Decision for AfterTaste:** Use the colourful Compose journal direction, with cobalt, peach, mint and lilac, a record motif, and real album artwork. Reserve monochrome for the launcher themed-icon layer. Pair large listening-time figures with clear labels, and use compact bars or dot plots for genre share and history volume. Keep a stable scale within each chart and preserve text labels so artwork or color is never the only key. Give the overview and Taste report room to breathe; keep dense history rows compact and scannable.

## Android platform checks

### MediaController transport controls

`MediaController` interacts with an ongoing `MediaSession`, retrieves its metadata, playback state, queue, and a `TransportControls` object. The controller can be created from a session token, or through `MediaSessionManager` when the app has `MEDIA_CONTENT_CONTROL` or is an enabled notification listener ([MediaController reference](https://developer.android.com/reference/android/media/session/MediaController)).

`MediaController.TransportControls` includes requests for play, pause, seek, skip to next, skip to previous, and other operations ([TransportControls reference](https://developer.android.com/reference/android/media/session/MediaController.TransportControls)). These methods send commands to the associated session; they do not guarantee that every session supports every command. Read `PlaybackState.getActions()` for the current supported action bitmask. The session owner must publish its state and handle commands through its callback ([PlaybackState reference](https://developer.android.com/reference/android/media/session/PlaybackState), [MediaSession reference](https://developer.android.com/reference/android/media/session/MediaSession)).

**Decision for AfterTaste:** A notification-listener-backed controller can provide transport controls for compatible active media sessions. Build controls from the selected controller's current playback actions, and treat commands as requests. Do not promise universal pause/seek/skip support across every player; the player session decides which operations it advertises and handles.

### Adaptive monochrome launcher icon

Android's adaptive icon guidance calls for foreground and background layers for the color icon, and a monochrome layer to support themed icons. Each layer is 108 x 108 dp; the central 66 x 66 dp is the unmasked logo area, with the outer 18 dp on each side reserved for masking and effects. Android recommends clean edges without masks or shadows in the artwork ([Android adaptive icons](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive)). The monochrome layer is used for themed icons on Android 13 and later when the user and launcher support theming; the same guidance says Android 16 QPR 2 can automatically theme icons that lack a supplied monochrome asset.

**Decision for AfterTaste:** Provide the monochrome layer in `mipmap-anydpi-v26` adaptive icon XML with the existing color foreground and background layers. Keep the mark centered and within the 66 dp safe zone at 108 dp canvas size. Use a single-color silhouette with no baked-in tint, background shape, or shadow so the launcher can apply the user's theme.

## Suggested screen model

- **Home:** Today's listening and recent tracks, plus a compact entry into the current period's insights.
- **History:** Search, date and genre filters, a date-grouped result list, and track-level session details.
- **Insights:** Listening habits with useful, explicit period comparisons.
- **Taste:** 1, 3, or 6 months; ranked genres by listening time with top songs and artist detail for the selected period.

Keep the labels, units, and time boundaries visible. Make every aggregate tappable through to the tracks that produced it. This gives the visual design room to feel personal while leaving the calculations easy to trust.
