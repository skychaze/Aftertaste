# AfterTaste long-term UX design input

## The scale problem

AfterTaste already stores durable daily totals and playback sessions in Room. Before this redesign, the analytics path observed every session through `getAllSessions()`, grouped it in `MainViewModel`, and placed the whole screen inside a `verticalScroll`. That worked for a small local history, but its cost grew with every listening session.

The design should make the overview cheap, and make detailed history an intentional query.

## Recommendations

### Keep the home screen summary-first

Use `daily_stats` for the first screen. Show today, the seven-day trend, the selected year, and genre totals from bounded aggregate queries. Load session rows only when the user opens a day, a month, a genre, or a history view. This keeps the first frame independent of lifetime session count.

[Last.fm describes scrobbling as building a listening profile from played tracks](https://www.last.fm/about/trackmymusic), then presents that history through weekly and yearly listening reports with period totals and top-level charts first ([Last.fm listening reports](https://www.last.fm/user/LastfmSupport/listening-report)). AfterTaste now uses Today, History, Insights, and Genres. Each destination leads with a summary and exposes detail on demand.

### Make history searchable and bounded

Add a History entry point with a persistent query bar and filters for date range, genre, artist, album, and source package. Offer a small set of useful sort orders: most recent, longest listening time, most played, and alphabetical. Spotify uses library filters, search, sorting, pinning, and list or grid views, with available options varying by collection. See [Your Library](https://support.spotify.com/ws/article/your-library/) and [Sort and filter](https://support.spotify.com/na-en/article/sort-and-filter/). YouTube Music similarly puts content filters for playlists, songs, albums, artists, and podcasts at the top of its library, and exposes genre filters for songs ([YouTube Music library filters](https://support.google.com/youtubemusic/answer/6313542?hl=en)).

For AfterTaste, the equivalent is a stable filter state that survives tab changes and process recreation. Do not present every session as the default home feed. Present track or day summaries first, then open the underlying sessions for inspection. Keep the current normalized title and artist fields as the grouping key, but expose the raw session timestamp and source in detail so the summary remains explainable.

### Query Room by the screen's scope

Replace lifetime reads in screen-facing paths with date-scoped DAO queries and projections. A daily detail screen needs sessions for one date. A monthly or yearly chart needs grouped totals, not full `PlaybackSessionEntity` objects. A history screen should order and filter in SQL, with a deterministic tie-breaker such as `id` after `startTime`.

Room supports DAO queries that return a `Flow`, and it can return a `PagingSource` directly from a DAO for Paging 3 ([Room DAO access](https://developer.android.com/training/data-storage/room/accessing-data)). `PagingSource` loads pages, provides a refresh key, and can be invalidated when its represented data changes ([PagingSource reference](https://developer.android.com/reference/androidx/paging/PagingSource)). Use that path only for an actually unbounded session history, after checking the project's dependency policy. It is unnecessary for the fixed seven-day chart or a twelve-month year chart.

The data model should keep these responsibilities separate:

- `daily_stats` answers overview questions.
- SQL aggregates answer chart questions.
- Session queries answer drill-down questions.
- A paged session query, if needed later, answers long history questions.

Do not reconstruct every chart by loading all sessions into Kotlin. That makes memory, query time, and recomposition grow together.

### Keep Compose work proportional to visible content

Compose's lazy layouts compose and lay out only items in the viewport, while a regular `Column` lays out the full collection ([lazy lists and grids](https://developer.android.com/develop/ui/compose/lists)). The broader [Compose performance guidance](https://developer.android.com/develop/ui/compose/performance) recommends stable keys for lazy layouts and avoiding unnecessary recomposition. Use `LazyColumn` for session and track histories, and give each row a stable key based on the persisted session id or a stable date key. Never use the displayed position as the key.

Keep sorting, grouping, and aggregation in the repository or `MainViewModel`. Android's Compose guidance warns that expensive calculations inside a composable can run frequently and recommends moving them out or caching them with `remember` ([Compose best practices](https://developer.android.com/develop/ui/compose/performance/bestpractices)). Keep the one-second now-playing state separate from historical lists so a live tick does not rebuild the entire history. State writes should happen in event or coroutine handlers, not during composition, because backwards writes can cause repeated recomposition.

The current root `verticalScroll` can remain for a short summary page. It should not contain an unbounded history list. If the summary grows, make the page itself lazy and keep nested scrolling limited to deliberate components such as the seven-day chart.

### Move maintenance out of first launch

Use WorkManager for cleanup, repair, and derived-data maintenance that can wait. WorkManager persists scheduled work across app restarts and device reboots, supports deferrable periodic work, and lets the app constrain when work runs ([persistent work](https://developer.android.com/develop/background-work/background-tasks/persistent)). Give maintenance a unique work name and use `KEEP` when a second enqueue should not create a duplicate; the API guarantees only one active continuation for a unique name ([WorkManager `enqueueUniqueWork`](https://developer.android.com/reference/androidx/work/WorkManager)).

A practical split is:

- Keep tracking and the five-second flush in the existing live engine.
- Keep only a small, correctness-critical recovery check before the first useful frame.
- Run stale-row cleanup, metadata repair, and any future summary backfill as unique deferrable work.

WorkManager is not a replacement for the active playback ticker or an exact-time event. It is for reliable maintenance that may run later.

### Measure the experience with a large fixture

Measure cold launch and the first useful overview on a release build. Android's startup guidance says complex initial composition and heavy main-thread calculations can increase launch time ([app startup](https://developer.android.com/topic/performance/vitals/launch-time)). The Macrobenchmark library measures startup and user interactions under a controlled environment, and Android recommends release builds because debug builds distort performance measurements ([performance test setup](https://developer.android.com/topic/performance/appstartup/setup-env)).

Benchmark at least an empty database, one year of data, and a multi-year history on a mid-range device. Record time to first usable overview, time to open a day, history scroll smoothness, and memory during a filtered query. The acceptance rule should be that adding history does not make the summary or first frame slower.

### Make larger screens useful

Use adaptive navigation based on window size. Android recommends a bottom navigation bar in compact windows and a navigation rail in expanded windows, with `NavigationSuiteScaffold` handling the switch ([adaptive navigation](https://developer.android.com/develop/adaptive-apps/guides/build-adaptive-navigation)). On a tablet or landscape window, show a history or day list beside its selected detail instead of replacing the whole screen. The official list-detail pattern uses a list pane and a detail pane on larger windows, then shows the detail alone when space is limited ([list-detail layout](https://developer.android.com/develop/adaptive-apps/guides/list-detail)).

For AfterTaste, the list pane can contain dates or track groups, while the detail pane shows listening time, play count, timestamps, genre, artwork, and source. This makes a long history inspectable without adding more top-level tabs. Adopt the adaptive scaffold only if the target device range justifies its dependency; the layout decision should not force an unplanned library expansion.

## Suggested rollout

1. Make summary screens consume bounded daily and aggregate queries, and isolate live ticks from historical state.
2. Add a searchable, filterable history screen backed by date-scoped SQL and a lazy list with stable keys.
3. Add WorkManager maintenance after startup work is bounded and idempotent.
4. Add a paged history query only when real fixtures show that a bounded query is no longer enough.
5. Add adaptive list-detail presentation and release Macrobenchmarks when larger screens and long-history fixtures are part of the supported product.

## Implemented in this pass

The app now runs one Room query pipeline for the active destination. Today reads sessions overlapping the current date and shows up to 30 grouped tracks. History reads 7, 30, or 90 daily rows and fetches tracks after a day is selected, Insights reads one year, and Genres reads SQL aggregates before fetching up to 100 tracks for a selected genre. Month and year genre totals correct only sessions crossing a range boundary. The root screen and drilldown lists use lazy layouts with stable keys. Startup cleanup waits until after the first useful tracking work and asks Room only for rows that match the cleanup criteria.

Paging, WorkManager, adaptive navigation rails, and Macrobenchmark were not added. The current dependency set can deliver bounded reads and deferred in-process cleanup without them. Those additions should follow measured need, especially if the app later exposes an unbounded session browser or promises cleanup after process death.
