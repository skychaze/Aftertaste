#!/usr/bin/env bash
# Read-only doctor for AfterTaste verification. Exit 0 = worth driving (Path A).
# For JVM-only work (Path B) only the build check matters; adb failures can be ignored.
set -u

SDK="${ANDROID_HOME:-/home/roy/Android/Sdk}"
ADB="$SDK/platform-tools/adb"
PKG="com.aistudio.ytmtracker.mplayq"
ACTIVITY="com.example.MainActivity"
APK="app/build/outputs/apk/debug/app-debug.apk"
fail=0

say() { printf '[doctor] %s\n' "$1"; }

if [ ! -x "$ADB" ]; then
  say "FAIL adb not found at $ADB"
  exit 1
fi

DEVICES="$("$ADB" devices | tail -n +2 | grep -v '^$')"
COUNT="$(printf '%s' "$DEVICES" | grep -c . || true)"
if [ "$COUNT" -eq 0 ]; then
  say "WARN no devices attached. Path A unavailable; use JVM path: ./gradlew :app:testDebugUnitTest :app:verifyRoborazziDebug"
  fail=1
elif [ "$COUNT" -gt 1 ]; then
  say "FAIL $COUNT devices attached; verification drives exactly one. Disconnect extras or use adb -s <serial>."
  fail=1
else
  SERIAL="$(printf '%s' "$DEVICES" | awk '{print $1}')"
  STATE="$(printf '%s' "$DEVICES" | awk '{print $2}')"
  if [ "$STATE" != "device" ]; then
    say "FAIL device $SERIAL is $STATE (offline/unauthorized)"
    fail=1
  else
    say "OK device $SERIAL online"
    if "$ADB" -s "$SERIAL" shell pm path "$PKG" >/dev/null 2>&1; then
      say "OK app installed on $SERIAL"
    else
      say "WARN app not installed; run: adb install -r $APK"
      fail=1
    fi
    LAUNCHABLE="$("$ADB" -s "$SERIAL" shell cmd package resolve-activity --brief -c android.intent.category.LAUNCHER "$PKG" 2>/dev/null | tail -n 1 | tr -d '\r')"
    case "$LAUNCHABLE" in
      *"$PKG"*) say "OK launcher activity resolvable ($LAUNCHABLE)" ;;
      *) say "WARN launcher activity not resolvable ($LAUNCHABLE)"; fail=1 ;;
    esac
  fi
fi

# Definitive and cheap when up-to-date (config-cached runs finish in ~1s):
# gradle rebuilds only if inputs actually differ, so mtime-only heuristics are
# avoided (git checkouts refresh mtimes without content changes).
if ./gradlew :app:assembleDebug --console=plain -q >/tmp/doctor-assemble.log 2>&1; then
  say "OK assembleDebug up to date ($APK)"
else
  say "FAIL assembleDebug errored; see /tmp/doctor-assemble.log"
  fail=1
fi

if [ "$fail" -eq 0 ]; then say "READY"; else say "NOT READY (fix the items above; WARN-level adb items can be skipped for JVM-only work)"; fi
exit "$fail"
