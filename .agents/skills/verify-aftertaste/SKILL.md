# Verify AfterTaste

Scripted verification for AfterTaste, an Android music time tracker (Kotlin, Jetpack Compose, Room, minSdk 24 / targetSdk 36). It records YouTube Music playback seconds via the notification listener and renders Daily / Weekly / Yearly / Genres analytics.

Surfaces:

- Android UI (single Compose activity, `com.example.MainActivity` in package `com.aistudio.ytmtracker.mplayq`, app label "AfterTaste"). Drive it with adb on a device or emulator.
- JVM verification path (Robolectric unit tests + Roborazzi screenshot verification) that runs without any device. This is the fallback when no device is attached, and it is the only path proven in this environment so far.

Read `features/README.md` first. It maps every user-facing feature to a drive recipe; a proof that only hits one convenient entry point is incomplete when the map lists others.

## Launch

Two paths. Pick based on what the doctor (below) reports.

### Path A: on-device (full verification)

1. Build the APK (CI parity: `assembleDebug` also runs lint and unit tests in CI):

   ```bash
   ./gradlew :app:assembleDebug
   ```

   Output: `app/build/outputs/apk/debug/app-debug.apk`.

2. Install on the attached device/emulator:

   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

3. Launch:

   ```bash
   adb shell am start -n com.aistudio.ytmtracker.mplayq/com.example.MainActivity
   ```

4. Ready signal: logcat prints `Displayed com.aistudio.ytmtracker.mplayq/com.example.MainActivity`:

   ```bash
   adb logcat -d | grep "Displayed com.aistudio.ytmtracker.mplayq"
   ```

   Or confirm the activity is on top: `adb shell dumpsys activity activities | grep mplayq`.

There is no server process to keep alive; the app runs on the device for the whole session. For teardown see Cleanup.

### Path B: JVM-only (no device)

```bash
./gradlew :app:testDebugUnitTest :app:verifyRoborazziDebug
```

`testDebugUnitTest` runs JVM + Robolectric tests; `verifyRoborazziDebug` compares recorded screenshots under `app/src/test/screenshots/` against the rendered output. A missing/differing screenshot fails with a diff report under `app/build/reports/roborazzi/`. This path exercises UI components (e.g. `NowPlayingCard`) but not navigation, database flush, or the notification listener. Anything those need requires Path A.

Note: Robolectric targets SDK 36 and may provision a JDK 21 toolchain via Foojay on first run (CI uses Temurin 21).

### Interactive sign-in mode (headless emulator + scrcpy mirror)

Positive tracking proofs need YouTube Music signed in. That is an interactive step; the user signs in through a visible mirror while the agent keeps driving via adb. On COSMIC/Wayland this setup is freeze-proof by construction: the emulator runs the `-headless` qemu binary with no X11 window at all (the windowed qemu X11 path freezes the compositor; see gotchas), and the only visible surface is scrcpy, a native Wayland client.

One command starts the whole session (built during this setup; it implements everything below):

```bash
.agents/skills/verify-aftertaste/launch.sh sign-in   # 5-core cap, 15fps mirror (dexopt headroom)
.agents/skills/verify-aftertaste/launch.sh drive     # 3-core cap, 30fps mirror (default)
```

Equivalent manual form:

```bash
# 1. Headless emulator: -headless qemu binary + stub xlib, no X11 window anywhere.
nohup env -u __NV_PRIME_RENDER_OFFLOAD __GLX_VENDOR_LIBRARY_NAME=mesa nice -n 10 \
  systemd-run --scope --user -p "CPUQuota=300%" -p "MemoryHigh=6G" \
  /home/roy/Android/Sdk/emulator/emulator -avd aftertaste-verify -gpu angle_indirect \
  -no-window -no-audio -no-boot-anim -no-snapshot -feature -Vulkan &
# 2. Wait for: adb shell getprop sys.boot_completed  ->  1
# 3. Silence guest ANR modals (they popped repeatedly during YT Music first-run dexopt):
adb shell settings put global send_action_app_error 0
# 4. Visible mirror for the user (scrcpy 4.1 static at /tmp/opencode/scrcpy-linux-x86_64-v4.1/scrcpy):
env SDL_VIDEODRIVER=wayland WAYLAND_DISPLAY=wayland-1 XDG_RUNTIME_DIR=/run/user/1000 \
  ADB=/home/roy/Android/Sdk/platform-tools/adb \
  /tmp/opencode/scrcpy-linux-x86_64-v4.1/scrcpy -s emulator-5554 \
  --max-fps 30 --max-size 720 --stay-awake --window-title "AfterTaste verify"
```

Confirm the log says `Graphics Adapter ... Mesa Intel(R) UHD Graphics`; `nvidia-smi` must show ~0% util. While the user signs in, do not tap credential fields; watch progress with `screencap` and drive the UI only before and after.

Gotchas learned on this setup, in the order they bit:

- Rendering the guest on the RTX (`__NV_PRIME_RENDER_OFFLOAD=1 __GLX_VENDOR_LIBRARY_NAME=nvidia`) freezes the whole COSMIC desktop when the guest screen updates continuously. Cause per research: NVIDIA's vsync'd GLX present under Xwayland has no sync mechanism (their own driver docs) and cosmic-comp's cross-GPU copy blocks its event loop (cosmic-comp #702/#211, #2022 open for the Xwayland interaction class; killing qemu unfreezes instantly). The iGPU recipe removes the NVIDIA present path.
- The windowed iGPU recipe still froze the desktop through the qemu Qt/XCB X11 window itself: interaction with continuously-repainting Xwayland windows can starve cosmic-comp regardless of GPU (#2022). Hence `-no-window` + scrcpy: the mirror is the community-endorsed pattern on this compositor (cosmic-comp #2614).
- Dropping `-feature -Vulkan` makes the API 36 guest's composer3 HAL thrash at 64% CPU (61% kernel, 144 major faults per ANR traces in `dumpsys dropbox`), starving the guest and looping "System UI isn't responding" during YT Music first-run dexopt. `-feature -Vulkan` is mandatory, not optional.
- The 3-core cap starved YT Music's first-run dexopt (59MB APK + animated splash). `launch.sh sign-in` widens to 5 cores and drops the mirror to 15fps until dexopt finishes; re-tighten afterwards. ANR traces live in `adb shell dumpsys dropbox --print system_app_anr` and name the culprit processes.
- scrcpy ships its own adb which kills the emulator SDK's adb server (scrcpy#2927). Always pass `ADB=/home/roy/Android/Sdk/platform-tools/adb`.
- The 15fps mirror is intentionally juddery; raise to `--max-fps 30` (+ `--max-size 720`) once first-run work settles. Host cost is trivial (~2.6% of one core); "laggy" claims inside the mirror are the fps cap or guest churn, not the host.
- scrcpy's window title bar cannot hang the compositor (native Wayland, libdecor); the Xwayland decoration-hover class of hangs does not apply.
- AVD data (installed apps, permission grants, Room DB, `wm size` overrides) survives emulator reboots; the notification listener grant persists too.

### No device installed at all?

This environment has the SDK at `/home/roy/Android/Sdk` with `adb` at `platform-tools/adb`, but no emulator package, no system images, and no AVDs. If `adb devices` is empty and you need Path A, install an emulator (large download; ask the user first):

```bash
/home/roy/Android/Sdk/cmdline-tools/latest/bin/sdkmanager "emulator" "platform-tools" "system-images;android-36;google_apis;x86_64"
/home/roy/Android/Sdk/cmdline-tools/latest/bin/avdmanager create avd -n aftertaste-verify -k "system-images;android-36;google_apis;x86_64" -d pixel_8
/home/roy/Android/Sdk/emulator/emulator -avd aftertaste-verify -no-window -no-audio -no-boot-anim &
# Wait for: adb shell getprop sys.boot_completed  ->  1
```

## Doctor

Run before anything else when a device is attached or something looks off. This is the repo's script (no user state changes; its build step writes only to `build/`):

```bash
.agents/skills/verify-aftertaste/doctor.sh
```

It checks, in order: `adb` present, exactly one device online, the build up to date (via gradle itself, so git-merge mtime refreshes don't false-positive), the app installed on the device, and the main activity launchable (`dumpsys package`, no side effects). Exit code 0 means "worth driving". JVM-only work (Path B) needs no device; for that, the build check is the relevant part and `adb` failures can be ignored.

## Drive

### Finding targets

The UI ships content descriptions instead of test tags. Dump the view hierarchy and pull it:

```bash
adb shell uiautomator dump /sdcard/ui.xml
adb pull /sdcard/ui.xml verification-artifacts/ui-<step>.xml
```

Then locate nodes by `content-desc` or `text` and read their `bounds="[l,t][r,b]"`. Tap the center: `adb shell input tap $(( (l+r)/2 )) $(( (t+b)/2 ))`.

Stable handles in the main screen (`app/src/main/java/com/example/ui/MusicTrackerScreen.kt` and `ui/components/`):

| Target | Handle | Kind |
|---|---|---|
| Tabs | `content-desc` "Daily" / "Weekly" / "Yearly" / "Genres" | icon (Compose tab) |
| Open YT Music | `content-desc` "Launch YouTube Music" | button in now playing card |
| Seed yearly data | `content-desc` "Seed Sample Data" | button in Yearly tab |
| Seed genre data | `text` "Load Sample Genre Data" | button in Genres tab (empty state) |
| Permission banner | `content-desc` "Permission Alert" | icon in banner |
| Info dialog | text "How YT Track Works" / "Got It" | dialog, shown on cold start |

### Granting notification listener access

Tracking depends on the listener service. Grant it without touching Settings UI:

```bash
adb shell cmd notification allow_listener com.aistudio.ytmtracker.mplayq/com.example.service.MusicNotificationListenerService
```

Note: the permission banner clears only when the app re-evaluates (restart it or bring it to front again), not instantly. `pm clear` on AfterTaste also revokes this grant (verified): re-run `allow_listener` and restart the app after any `pm clear`.

### YouTube Music on the AVD

The `google_apis` AVD ships a prebuilt YouTube Music (`/product/app/YouTubeMusicPrebuilt/`, signed with the emulator's system key). Use it; a newer APKPure/Play APK will fail `adb install-multiple` with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` because signatures differ from the preinstalled system copy. If a newer version is truly needed, `pm uninstall --user 0` the prebuilt first, then install the downloaded XAPK's base + language + density splits via `adb install-multiple`. Playback sign-in is an interactive step; use the visible emulator recipe above.

```bash
adb shell monkey -p com.google.android.apps.youtube.music -c android.intent.category.LAUNCHER 1
```

### Known app behavior (verified on the emulator, 2026-09-05)

These are findings, not bugs to re-litigate; factor them into every drive:

- **Seeding is invisible to the live engine.** Seed buttons write Room rows directly; the engine's `todayTotalSeconds` stays at whatever it rehydrated at boot. Daily/Weekly under-report today until an app restart. Restart the app after seeding before judging today-facing UI.
- **Rehydration works.** On restart the engine loads today's `daily_stats` row (`loadTodayStatFromDb`); verified: restart showed 1h 17m / 100% of a 60m goal matching the DB.
- **Weekly's "today" bar uses the live counter, not the DB** (`MainViewModel.kt:475`). With a stale counter the weekly total excludes today's DB minutes exactly. Same staleness rule as above.
- **Per-genre/genre hour labels floor to whole hours.** 48 min renders "0 Hours", 1.91h renders "1 Hour". Percentages match the DB exactly; only the hour labels floor. A "0 Hours at 10.3%" row is correct math, not a bug.
- **The permission banner clears only when the app re-evaluates** (restart or re-foreground), not when `allow_listener` lands. Plan a restart into the drive.
- Year analytics header shows the total two ways: "15 Days 5 Hours" (24h days) and "365h 55m"; both matched DB math. Selected-genre card text can overlap ("3 Days 5 HoursPost Malone, Tra…") — cosmetic nit, capture as such.

### Simulating playback

Real tracking needs a media session. The honest path on this setup is the signed-in YT Music account on the AVD: start playback through "Feeling lucky" (or any track), then drive playback state from the shell. VERIFIED recipe (2026-09-05):

```bash
adb shell cmd media_session dispatch play     # resume; pause / next / previous also work
adb shell cmd media_session dispatch pause    # tracking freezes, DB flush stops at paused value
adb shell cmd media_session dispatch next     # NOT "skip-to-next" (errors)
adb shell dumpsys audio | grep "state:started" # confirm USAGE_MEDIA is started (pid = YT Music)
```

Confirmed behavior with real audio (all measured): the live timer ticks per second, DB today grows in 5s flush steps (`daily_stats` 4620 -> 4765s across the run), a `playback_sessions` row appears per flushed track with `sourcePackage=com.google.android.apps.youtube.music`, on-screen "N tracks played" equals DB `sessionCount`, pause freezes the flush at the paused value, and tracks under 5 seconds are discarded (4s skips left no rows while a 6s skip was kept).

A raw `dispatch play` on a bare emulator with no YT Music session has no effect and the tracker legitimately shows nothing. That is a valid negative result: capture it as one. For positive results, play real audio (needs sign-in; use launch.sh sign-in) or seed data (buttons above). Do not fake DB rows by writing to Room directly; that tests the UI against data the engine would never produce.

### Observing the database

Debug builds are debuggable, so the Room DB is readable via run-as. Host sqlite3 lives in platform-tools:

```bash
adb shell run-as com.aistudio.ytmtracker.mplayq ls databases/
adb exec-out run-as com.aistudio.ytmtracker.mplayq cat databases/<name> > verification-artifacts/db-<step>.sqlite
sqlite3 verification-artifacts/db-<step>.sqlite "SELECT * FROM playback_sessions LIMIT 5;"
```

The engine flushes to the DB every 5 seconds and discards sessions under 5 seconds, so short interactions leave no row. Wait at least 15 seconds of active playback before expecting rows.

## Evidence

Capture into `verification-artifacts/` at the repo root (gitignored). Name files by feature and step, e.g. `yearly-tab-after-seed.png`.

- Screens: `adb exec-out screencap -p > verification-artifacts/<name>.png`
- Hierarchy: the `uiautomator dump` pull above
- Logs: `adb logcat -d > verification-artifacts/logcat-<name>.txt`
- DB: the run-as pull above
- JVM path: `app/build/test-results/testDebugUnitTest/*.xml` and `app/build/reports/roborazzi/` — copy the relevant files into `verification-artifacts/` since `app/build/` is wiped by clean

Proof standards:

- Drive the real user path: launch, tap tabs by their on-screen handles, watch values change. Do not poke view models or test-only seams.
- Capture the action and the resulting state: the tap and the screen after it, the seed click and the DB rows it created.
- Verify side effects, not just pixels: a seed click is proven by rows in `daily_stats`/`playback_sessions` plus the changed UI, not by one screenshot.
- Mock nothing on Path A. The tracker's guards (placeholder metadata rejection, sub-5-second discard, YouTube-main exclusion) are the interesting behavior; driving around them with fakes proves nothing.
- On Path B, `verifyRoborazziDebug` failing on first run may mean the baseline needs re-recording (`recordRoborazziDebug`), not that the app broke. Inspect the diff before deciding. Known instance: the committed `src/test/screenshots/greeting.png` was a truncated non-PNG, which made every verify run NPE inside Roborazzi's image loader until the baseline was re-recorded. If a fresh clone fails the same way, re-record and commit the new baseline.

## Cleanup

Tear down what the run created, in this order:

```bash
adb shell am force-stop com.aistudio.ytmtracker.mplayq
adb shell pm clear com.aistudio.ytmtracker.mplayq   # wipes app data only if the instance was created by this run
adb uninstall com.aistudio.ytmtracker.mplayq        # only if this run installed the APK
adb shell cmd notification disallow_listener com.aistudio.ytmtracker.mplayq/com.example.service.MusicNotificationListenerService
```

- Never kill by process name; the commands above target the package you installed.
- `pm clear` destroys the user's real tracking data. On a physical device that holds the user's listening history, skip `pm clear` and `uninstall` unless the run created the install.
- If an emulator was booted for the run: `adb -s <serial> emu kill`.
- Evidence survives: nothing in `verification-artifacts/` is removed by cleanup. Confirm the files are still there after teardown.

## Isolation

The app is single-instance per device and holds one Room DB. Two verification instances side by side means two devices: `adb -s <serial>` prefixes every command. Do not run two agents against one attached device; refuse and serialize instead. If the attached device is the user's daily phone, treat its data as untouchable (see Cleanup) and prefer a fresh emulator.

## Feature map

`.agents/skills/verify-aftertaste/features/README.md` indexes one file per feature: now playing card, Daily, Weekly, Yearly, Genres. Each maps user-visible behavior to drive steps and observable end states. Keep it current when the UI changes.
