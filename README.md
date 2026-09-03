# AfterTaste

Music time tracker for Android. It records only the seconds YouTube Music is actively playing and turns them into daily, weekly, yearly, and genre analytics.

![Build](https://github.com/skychaze/Aftertaste/actions/workflows/android-ci.yml/badge.svg)
![Min SDK](https://img.shields.io/badge/minSdk-24%20(Android%207.0)-blue)
![Target SDK](https://img.shields.io/badge/targetSdk-36-blue)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.09.00-4285F4?logo=jetpackcompose&logoColor=white)
![Room](https://img.shields.io/badge/Room-2.7.0-lightgrey)
![License](https://img.shields.io/badge/license-TBD-lightgrey)

## Contents

- [What it does](#what-it-does)
- [What it does not track](#what-it-does-not-track)
- [Features](#features)
- [Screenshots](#screenshots)
- [Tech stack](#tech-stack)
- [How tracking works](#how-tracking-works)
- [Project structure](#project-structure)
- [Data model](#data-model)
- [Getting started](#getting-started)
- [Configuration](#configuration)
- [Usage](#usage)
- [Permissions](#permissions)
- [Privacy](#privacy)
- [Testing and CI](#testing-and-ci)
- [Build variants and signing](#build-variants-and-signing)
- [Troubleshooting](#troubleshooting)
- [FAQ](#faq)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [License](#license)
- [Acknowledgements](#acknowledgements)

## What it does

AfterTaste listens to active media sessions and YouTube Music notifications, counts playback time second by second while audio is playing, and stores the result locally in Room. The dashboard shows a live now playing card with a moving position timeline, plus four analytics tabs for daily, weekly, yearly, and genre views.

Core facts:

- Source of truth is `MediaController` playback state plus `AudioManager.isMusicActive`, not app foreground time.
- A per second ticker adds to the current session and the today total, and flushes to the database every 5 seconds.
- Sessions shorter than 5 seconds are discarded as skips or ghosts, with daily totals rolled back.
- Track repeats are absorbed into the same session row and counted through `playCount`, so loops render as `2x`, `3x` instead of duplicate rows.
- YouTube video apps are excluded everywhere. Only YouTube Music counts by default, with an optional toggle to include other music apps.

## What it does not track

- Time the app spends open or in the background. Paused or stopped playback stops the counter immediately.
- YouTube main, Kids, or TV playback. Package and metadata guards in `YouTubeHelper` reject these.
- Placeholder metadata such as `No music playing`, `Unknown Track`, or bare `YouTube Music` strings. These never create sessions.

## Features

Now playing:

- Live title, artist, album, genre, artwork, and session timer.
- Live position and duration timeline driven by estimated playback position.
- One tap button to open YouTube Music, with a web fallback to `music.youtube.com`.
- Permission banner that opens the notification listener settings when access is missing.

Daily tab:

- Today total, session count, and configurable daily goal in minutes.
- Today track feed grouped by normalized title and artist, with per track seconds and play counts.
- Live row for the currently playing track updates every second.

Weekly tab:

- Last 7 day histogram with per day minutes.
- Week average and per day unique track drill down.

Yearly tab:

- 12 month histogram with hours, active days, peak month, year total, and daily average.
- Listener milestones at 5, 25, 50, 100, and 250 hours.
- Consecutive day streak.
- Month detail with day grid, week breakdown, and weekday versus weekend averages.
- Sample data seeder for previewing charts on an empty database.

Genres tab:

- Pie style distribution across month, year, and all time scopes.
- Dominant genre, per genre minutes and percentages, top artists, and unique tracks per genre.

Data quality:

- Normalizes titles by stripping tags like official video, lyrics, audio, live, remix, and feat credits.
- Cleans artist strings by removing suffixes like `Topic` and feat credits.
- Reattaches to the open session after a process restart within a 2 minute window instead of inserting a duplicate.
- Nightly style cleanup on startup removes corrupt rows, YouTube video rows, placeholder artists, and short sessions, then resyncs daily stats from sessions.

## Screenshots

No screenshots are checked in yet. To add them, drop PNG files into `docs/screenshots/` and reference them here.

Suggested set:

1. Now playing card while a track plays.
2. Daily tab with today feed.
3. Weekly histogram with a selected day.
4. Yearly histogram with milestones.
5. Genre distribution.

Example once files exist:

```markdown
![Now playing](docs/screenshots/now-playing.png)
![Daily](docs/screenshots/daily.png)
![Weekly](docs/screenshots/weekly.png)
![Yearly](docs/screenshots/yearly.png)
![Genres](docs/screenshots/genres.png)
```

## Tech stack

| Area | Choice |
|---|---|
| Language | Kotlin 2.2.10, Java 11 bytecode |
| UI | Jetpack Compose, Material 3, Compose BOM 2024.09.00 |
| Architecture | Single activity, `MainViewModel` with StateFlow, `MusicTrackerEngine` singleton |
| Storage | Room 2.7.0 with KSP, `DailyStatEntity` and `PlaybackSessionEntity` |
| Async | Kotlin coroutines and Flow |
| Network | Retrofit 2.12.0, Moshi 1.15.2 with codegen, OkHttp 4.10.0 |
| Images | Coil 2.7.0, plus local artwork cache under app cache dir |
| Genre sources | Local heuristic classifier, Spotify Web API through client credentials flow, iTunes Search API |
| Firebase | Firebase AI, App Check with Recaptcha and debug providers, google-services passthrough enabled |
| Config | Secrets Gradle plugin reading `.env` with `.env.example` defaults |
| Tests | JUnit 4, Robolectric 4.16.1, Roborazzi 1.59.0 screenshot tests |
| Build | AGP 9.1.1, Gradle 9.3.1 wrapper, Foojay toolchain resolver |
| CI | GitHub Actions, JDK 21 Temurin, assemble plus unit tests plus lint |

## How tracking works

The pipeline has four stages.

1. Detect. `MusicNotificationListenerService` subscribes to active media sessions and transport notifications. It forwards YouTube Music controllers to the engine and extracts title, artist, album, and artwork from notification extras when no session token is present.
2. Decide. `MusicTrackerEngine` checks playback state, package, and metadata. It rejects YouTube video packages, honors the YouTube Music only filter, ignores placeholders, and matches incoming metadata to the current track with normalized title and artist comparison.
3. Count. When a new real track starts, the engine inserts a `PlaybackSessionEntity` and starts a 1 second ticker. Every tick increments session seconds and today seconds. Every 5 seconds it flushes to Room. Pause or stop flushes immediately and halts the ticker.
4. Explain. `MainViewModel` combines engine state with all daily stats and all sessions, groups them into unique tracks, day buckets, month buckets, and genre slices, and caches the result. Per second ticks patch only the live timer values instead of rebuilding every chart.

Loop handling deserves a note because repeat behavior is easy to get wrong. The engine records the maximum observed position per session. A loop is declared when position rewinds to near zero after at least 15 seconds of progress, when session time passes track duration and position wraps, or when position drops from past 80 percent to under 10 seconds. The loop increments `playCount` on the same row. Listening time keeps accumulating with no new row.

Genre resolution runs in two passes. The engine first applies the instant local `GenreClassifier` so UI and database rows never wait on network. It then queries Spotify when credentials exist, falling back to iTunes Search, and updates the row and UI only when the resolved genre differs.

Artwork resolution follows the same pattern. Media metadata bitmaps and art URIs win first and are saved to the app cache dir. iTunes artwork fills gaps later. Cached paths persist on the session row.

## Project structure

```text
.
├── app/
│   ├── build.gradle.kts            # App module, SDK levels, signing, dependencies
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml # Permissions, queries, listener service
│       │   ├── java/com/example/
│       │   │   ├── MainActivity.kt
│       │   │   ├── YTTrackerApplication.kt
│       │   │   ├── data/           # Room entities, DAO, repository
│       │   │   │   ├── AppDatabase.kt
│       │   │   │   ├── DailyStatEntity.kt
│       │   │   │   ├── MusicTrackerDao.kt
│       │   │   │   ├── MusicTrackerRepository.kt
│       │   │   │   └── PlaybackSessionEntity.kt
│       │   │   ├── tracker/        # Detection, genre, artwork helpers
│       │   │   │   ├── MusicTrackerEngine.kt
│       │   │   │   ├── YouTubeHelper.kt
│       │   │   │   ├── GenreClassifier.kt
│       │   │   │   ├── MusicGenreResolver.kt
│       │   │   │   ├── SpotifyGenreResolver.kt
│       │   │   │   ├── ITunesSearchApi.kt
│       │   │   │   └── ArtworkResolver.kt
│       │   │   ├── service/
│       │   │   │   └── MusicNotificationListenerService.kt
│       │   │   ├── ui/
│       │   │   │   ├── MainViewModel.kt
│       │   │   │   ├── MusicTrackerScreen.kt
│       │   │   │   ├── theme/
│       │   │   │   └── components/ # Now playing, daily, weekly, yearly, genre views
│       │   │   └── util/
│       │   │       └── TimeFormatUtils.kt
│       │   └── res/                # Strings, colors, themes, launcher icons
│       ├── test/                   # Unit tests, Robolectric tests, Roborazzi tests
│       └── androidTest/            # Instrumented tests
├── gradle/
│   ├── libs.versions.toml          # Version catalog
│   └── wrapper/
├── .github/workflows/android-ci.yml
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── .env.example
└── README.md
```

## Data model

Room database holds two tables.

`daily_stats`, one row per calendar day:

- `date` as `yyyy-MM-dd`, primary key, plus `year`, `month`, `day`, `dayOfWeek`.
- `totalPlayTimeSeconds` and `sessionCount`.
- `lastUpdatedTimestamp`.

`playback_sessions`, one row per tracked listen:

- `date`, `year`, `month`, `startTime`, `endTime`, `durationSeconds`.
- `title`, `artist`, `album`, `genre`, `artworkUrl`, `sourcePackage`.
- `playCount`, incremented when the same track loops inside the session.

Repository rules worth knowing:

- `addListeningTime` and `incrementSessionCount` use atomic DAO increments so ticker flushes and pause flushes cannot lose seconds.
- `deleteShortSessions` removes rows under 5 seconds on startup.
- `cleanCorruptSessions` also deletes YouTube video rows and resyncs every daily stat from surviving sessions.

## Getting started

### Prerequisites

- Android Studio Ladybug or newer, with Android SDK 36 installed.
- JDK 21 for unit tests. CI uses Temurin 21. Local test workers auto provision JDK 21 through the Foojay toolchain plugin.
- A device or emulator running Android 7.0 or newer, API 24 plus.
- YouTube Music installed for live tracking. Other behavior can be previewed with the sample data seeder.

### Clone and build

```bash
git clone https://github.com/skychaze/Aftertaste.git
cd Aftertaste
./gradlew :app:assembleDebug
```

The APK lands under `app/build/outputs/apk/debug/`. Install it with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Run tests and lint

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

CI runs all three in one step:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --stacktrace
```

Note: Robolectric targets SDK 36 here, so unit tests launch under a JDK 21 toolchain. The first run may download a JDK through Foojay.

## Configuration

### Notification listener access, required

Live tracking needs notification listener access. Without it the app falls back to a coarse `AudioManager` check and shows the permission banner.

Steps:

1. Open AfterTaste.
2. Tap the banner action to open system notification listener settings.
3. Enable AfterTaste in the list.
4. Return to the app and play something in YouTube Music.

The service class is `com.example.service.MusicNotificationListenerService` and it requires `BIND_NOTIFICATION_LISTENER_SERVICE`.

### Spotify genre API, optional

Out of the box genres come from the local heuristic plus iTunes Search. For official Spotify artist genres, add Spotify Developer credentials in the app through the Spotify config dialog. The app uses the client credentials flow, caches the access token in memory, and stores the ID and secret in private shared preferences. No Spotify login is needed.

You can request credentials at the Spotify developer dashboard, then enter the client ID and client secret in the app and use the test connection action before saving.

### Gemini API key, optional and currently unused by tracking

`.env.example` contains a commented `GEMINI_API_KEY` placeholder wired through the Secrets Gradle plugin. Uncomment and set it only if you enable a Gemini backed feature. As checked in, tracking, genres, and artwork do not need it.

```bash
cp .env.example .env
```

Then edit `.env`:

```properties
GEMINI_API_KEY=your_key_here
```

`.env` is git ignored. Never commit keys.

### Firebase, optional

`google-services.json` is not required to build. `googleServices.missing.passthrough` is true and the missing services strategy is warn, so debug builds work without the file. Add `app/google-services.json` only if you use Firebase AI or App Check against a real project. That file is git ignored by the standard Android setup, keep it that way.

## Usage

1. Grant notification listener access as described above.
2. Play music in YouTube Music. The now playing card switches to active, the session timer starts, and the position bar moves.
3. Pause the music. The timer freezes and the accumulated seconds flush to Room.
4. Open the Daily tab to see today total, goal progress, and the grouped track feed.
5. Switch to Weekly, Yearly, or Genres for longer views. Tap bars and genre slices to reveal the unique tracks behind each bucket.
6. Set a daily goal in minutes from the Daily tab. The value persists in shared preferences.
7. On an empty install, use the seed sample data action on the Yearly or Genres tab to preview charts with realistic history.

## Permissions

| Permission | Where | Why |
|---|---|---|
| `INTERNET` | Manifest | Spotify genre lookup, iTunes genre and artwork lookup, Firebase calls |
| `POST_NOTIFICATIONS` | Manifest, runtime on Android 13 plus | Local playback notifications if enabled by the system path |
| `FOREGROUND_SERVICE` | Manifest | Declares foreground service capability |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Listener service | Read active media sessions and transport notifications |
| Package queries for YouTube Music, YouTube, and music intents | Manifest `queries` | Detect the correct source package and offer an open action |

The app requests no location, contacts, storage, or microphone permissions.

## Privacy

- Listening history stays on device in the app Room database.
- Backup rules exist under `app/src/main/res/xml/backup_rules.xml` and `data_extraction_rules.xml`. Review them before enabling cloud backup for sensitive history.
- Network calls go to Spotify accounts and API hosts plus iTunes Search, and to Firebase only when configured. Payloads are artist, title, and album strings for genre and artwork resolution.
- Spotify credentials are stored in private app preferences and never leave the device except to Spotify token and search endpoints.
- Analytics, crash reporting, and ads are not part of the checked in code path.

## Testing and CI

- `app/src/test` holds JVM unit tests, Robolectric tests, and Roborazzi screenshot tests.
- `app/src/androidTest` holds instrumented tests for device runs.
- `.github/workflows/android-ci.yml` runs on pushes to `main` and on pull requests. It checks out the repo, sets up JDK 21, generates a throwaway `debug.keystore` when missing, then runs `assembleDebug`, `testDebugUnitTest`, and `lintDebug`.

Recent test history in this repo covers loop absorption, play count labels, session reattach after restart, placeholder artist cleanup, and locale formatting.

## Build variants and signing

- `debug` signs with `debug.keystore` at the repo root using the standard `android` credentials. CI generates this file automatically when absent. The root `.gitignore` excludes `.env`, `local.properties`, and `debug.keystore`, so do not commit yours.
- `release` expects a keystore at `KEYSTORE_PATH` or `my-upload-key.jks` at the repo root, with `STORE_PASSWORD` and `KEY_PASSWORD` from the environment and alias `upload`. There is no checked in release key.
- `minSdk` is 24, `targetSdk` and `compileSdk` are 36. PNG crunching is off for release and minification is off, with the standard optimize ProGuard file plus `proguard-rules.pro` referenced for future use.

Example release build:

```bash
export KEYSTORE_PATH=/path/to/my-upload-key.jks
export STORE_PASSWORD='...'
export KEY_PASSWORD='...'
./gradlew :app:assembleRelease
```

## Troubleshooting

App shows waiting and never tracks:

- Confirm notification listener access is enabled for AfterTaste, then return to the app. `onResume` rescans sessions.
- Confirm sound is actually playing. The engine checks `isMusicActive` before accepting notification metadata.
- Confirm the source is YouTube Music. Main YouTube app playback is rejected by design.
- If you want Spotify or other players counted, disable the YouTube Music only filter in the app.

Track shows wrong genre:

- The first label is always the local heuristic for speed. Spotify or iTunes resolution updates it seconds later when network is available.
- Add Spotify credentials if you want official artist genres instead of keyword classification.

Artwork is blank:

- Some sessions expose no bitmap or art URI. The app tries media metadata first, then cached art, then iTunes. Very new or obscure tracks may have no match.

Unit tests fail with toolchain or SDK errors:

- Install JDK 21 and rerun. The Gradle config pins test workers to Java 21 for the Robolectric SDK 36 sandbox.
- Delete a stale `debug.keystore` only if keytool reports an alias conflict, then let CI or Gradle recreate it.

Release build fails on signing:

- Check that `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD` are set and that the key alias is `upload`.

## FAQ

Is this a YouTube Music downloader or player?

No. It only observes playback metadata and counts seconds. It cannot play, download, or modify music.

Does it work with the screen off?

Yes. Tracking follows audio playback state, not screen state or app foreground state.

Does it support Spotify playback tracking?

Detection code recognizes Spotify packages when the YouTube Music only filter is off, but the product focus and default filter are YouTube Music. Spotify credentials in the app are for genre resolution, not for Spotify playback import.

Where is my data?

In the app private database on the device. Clearing app data deletes history. There is also a clear all data action behind the ViewModel path used during development.

Why do repeats show as `3x`?

Loops stay in one session row and bump `playCount`. This keeps the feed free of duplicates while preserving how many times a track restarted.

## Roadmap

- Checked in screenshots and a short screen recording in `docs/`.
- Export and import for local backup, likely JSON or CSV.
- Home screen widget for today progress.
- Optional per artist and per album leaderboards.
- Better offline behavior and retry for genre and artwork resolution.
- Release signing docs and a Play listing checklist once a license is chosen.

Suggestions are welcome through issues. Small focused pull requests are easier to review than large ones.

## Contributing

1. Fork the repo and create a short branch such as `fix/loop-edge-case` or `feat/export-history`.
2. Run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --stacktrace` before pushing.
3. Keep pull requests under about 600 lines when possible. Use stacked PRs for larger work.
4. Follow the existing Kotlin official style. Do not use `Any` as an escape hatch, prefer inferred types, and keep comments short and current.
5. Do not commit `.env`, `google-services.json`, keystores, or local IDE files.

By contributing you agree your changes can be released under the license the project adopts.

## License

No license file is checked in yet, so all rights remain with the authors by default. If you plan to fork or redistribute, open an issue first. The recommended next step for the maintainer is to add a `LICENSE` file, commonly MIT or Apache-2.0 for an app like this, and then update this section with the license name and link.

## Acknowledgements

- YouTube Music for the playback source.
- Spotify Web API and iTunes Search API for genre and artwork resolution.
- Jetpack Compose, Room, Retrofit, Moshi, Coil, and OkHttp maintainers.
- Robolectric and Roborazzi for JVM and screenshot testing.
- Firebase for AI and App Check building blocks.
