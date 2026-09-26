# Updates

In-app update checker in the header. The "App updates" icon opens a dialog that checks the newest GitHub release, shows its release notes, downloads the APK through DownloadManager with progress, and hands it to Android's package installer. The dialog explains that Android will ask the user to confirm installation.

## Sub-features

- Check: reads `https://api.github.com/repos/skychaze/Aftertaste/releases/latest`, validates the tag and APK asset, and compares against the installed version.
- Available update: shows up to four release-note lines when provided and labels the primary action "Download update".
- Status states: IDLE, CHECKING, UP TO DATE, AVAILABLE, DOWNLOADING (percent plus network wait), READY, ERROR (check, download, verify, install).
- Download: DownloadManager writes to `Android/data/com.aistudio.ytmtracker.mplayq/files/updates/`, shows a completion notification, and the app polls progress once per second.
- Restart recovery: force-stop the app mid-download; relaunch restores the download at its real progress.
- Verification: size, the sha256 digest from the release payload when present, package name, and signer must match before READY.
- Install: unknown-sources settings detour on first install, then the package installer; returning to the app resumes the install automatically.

## How to get to it (user POV)

Header, right side, `content-desc` "App updates" (rightmost icon, right of the App Info icon). Tap it; the dialog checks on open.

## Driving it with adb

1. Tap the header `content-desc` "App updates". The dialog opens showing `Installed v<name> (<code>)` and a status line.
2. Check: the dialog runs a check on open. Wait ~3s and dump the hierarchy; read the status text.
3. Against a newer valid release, the dialog reports that an update is available, shows release notes when provided, and offers "Download update".
4. Build with `-PversionName=99.0` to prove the up-to-date path: the dialog reads `You are on the latest version.` with "Check again".
5. Download: tap "Download update". With a real release the download runs from GitHub; expect a progress bar and percentage, then `Update downloaded. Install to finish.` A release-signed APK fails signer verification against a debug build by design: expect `The downloaded update failed verification.` and no installer.
6. Install detour: on a fresh install, tap "Install". Android opens `Settings > Install unknown apps` for AfterTaste. Toggle "Allow from this source"; returning to the app opens the package installer automatically.
7. Evidence: screenshots of each state, and `dumpsys package com.aistudio.ytmtracker.mplayq | grep versionName` after a successful replacement.

## Proof standards

- The full replacement loop needs matching signatures, which release APKs do not share with debug builds. The throwaway prototype branch `prototype/ota-update` proves check -> download -> restart recovery -> verify -> unknown-sources -> installer -> in-place replacement with debug-signed 9.9.9 (999) over 1.0 (1). Evidence: `verification-artifacts/proto-*.png`.
- Production proof on this AVD: real GitHub check, real download with progress, signer rejection, and the up-to-date path. Replacement is proven by the prototype.
- After any successful install, `dumpsys package` must show the new versionCode/versionName and a fresh check must read `You are on the latest version.`

## Gotchas

- Unauthenticated GitHub API allows 60 requests/hour per IP. Repeated checks can return 403; the dialog then shows `Could not check for updates.` Retry later.
- The updater needs the versioned asset name (`aftertaste-v<versionName>-<versionCode>.apk`) for exact version-code comparison; older releases fall back to comparing version names.
- DownloadManager keeps downloading when the app process dies; the app restores state on next launch. The dialog does not poll while closed.
- `pm clear` wipes the update prefs and the downloaded file, so recovery testing must restart the app without clearing data.
- The signer check means a debug build can never install a release APK; use the prototype branch or a debug-signed newer build for replacement proofs.
- The App Info icon and update icon both live in the header; stale coordinates after the permission banner appears/disappears can hit the wrong icon (see SKILL.md coordinate discipline).
