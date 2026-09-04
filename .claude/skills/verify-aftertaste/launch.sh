#!/usr/bin/env bash
# Freeze-proof AfterTaste verification session on COSMIC/Wayland hybrid laptops.
# Starts the emulator headless (no X11 window; the windowed qemu X11 path freezes
# cosmic-comp, see SKILL.md gotchas) plus a scrcpy Wayland mirror the user can
# see and interact with. Usage:
#   launch.sh [sign-in|drive]   sign-in: 5-core cap (dexopt headroom); drive: 3-core cap
set -u
PHASE="${1:-drive}"
SDK="${ANDROID_HOME:-/home/roy/Android/Sdk}"
EMU="$SDK/emulator/emulator"
ADB="$SDK/platform-tools/adb"
SCRCPY_BIN="/tmp/opencode/scrcpy-linux-x86_64-v4.1/scrcpy"   # scrcpy 4.1 static, see SKILL.md
QUOTA="300%"
FPS="30"
if [ "$PHASE" = "sign-in" ]; then QUOTA="500%"; FPS="15"; fi

# Share the SDK adb server with scrcpy (scrcpy ships its own adb; two versions
# kill each other's server).
if [ ! -x "$SCRCPY_BIN" ]; then
  echo "[launch] $SCRCPY_BIN missing; download scrcpy 4.1 per SKILL.md" >&2
  exit 1
fi

"$ADB" -s emulator-5554 emu kill >/dev/null 2>&1
sleep 3

nohup env -u __NV_PRIME_RENDER_OFFLOAD __GLX_VENDOR_LIBRARY_NAME=mesa nice -n 10 \
  systemd-run --scope --user -p "CPUQuota=$QUOTA" -p "MemoryHigh=6G" \
  "$EMU" -avd aftertaste-verify -gpu angle_indirect \
  -no-window -no-audio -no-boot-anim -no-snapshot -feature -Vulkan \
  > /tmp/opencode/emulator-verify.log 2>&1 &

for i in $(seq 1 40); do
  sleep 10
  [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break
done
"$ADB" shell settings put global send_action_app_error 0   # no modal ANR dialogs over sign-in
echo "[launch] booted; guest fps+churn tuning applied"

nohup env SDL_VIDEODRIVER=wayland WAYLAND_DISPLAY="${WAYLAND_DISPLAY:-wayland-1}" \
  XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/run/user/$(id -u)}" ADB="$ADB" \
  "$SCRCPY_BIN" -s emulator-5554 --max-fps "$FPS" --max-size 720 --stay-awake \
  --window-title "AfterTaste verify" > /tmp/opencode/scrcpy-verify.log 2>&1 &
echo "[launch] mirror up at ${FPS}fps, cap $QUOTA (phase: $PHASE)"
echo "[launch] confirm renderer: grep 'Graphics Adapter' /tmp/opencode/emulator-verify.log  (must say Mesa Intel)"
