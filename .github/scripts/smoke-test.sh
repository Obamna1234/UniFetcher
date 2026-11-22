#!/usr/bin/env bash
# UniFetcher smoke test on a running Android emulator. Usage:
#   smoke-test.sh <new apk> <package> [last release apk] [strict real-site check: true|false]
#
# 1. Update test: installs the last release (if given), opens it, then installs the new build over
#    it and checks Android kept the app's data (same first-install time) instead of reinstalling.
# 2. Start-up test: opens the new build and checks it's still running 25 seconds later.
# 3. Download test: shares a test video to UniFetcher (a clip made on the CI machine and served at
#    http://10.0.2.2:8000), taps Download, and checks the file arrives, plays (video + audio) and
#    wasn't re-encoded (same codecs and size as the original).
# 4. Real-site test: the same with a freely licensed file on Wikimedia Commons. A failure here is a
#    warning on normal pushes (the site may be down) and an error on the daily run.
set -u
APK="$1"
PKG="$2"
OLD_APK="${3:-}"
STRICT_REAL="${4:-false}"
OUT="smoke-test-logs"
DL="/sdcard/Download/UniFetcher"
mkdir -p "$OUT"
FAILED=0

note() { # level title message
  local msg="${3//'%'/'%25'}"
  msg="${msg//$'\r'/}"
  msg="${msg//$'\n'/'%0A'}"
  echo "::$1 title=$2::$msg"
}

first_install_time() {
  adb shell dumpsys package "$PKG" | grep -m1 firstInstallTime | tr -d '\r' | sed 's/.*firstInstallTime=//'
}

crash_lines() {
  adb logcat -d > "$OUT/logcat.txt" || true
  grep -n -A60 -E "FATAL EXCEPTION|Process: $PKG|>>> $PKG <<<" "$OUT/logcat.txt" > "$OUT/crash.txt" || true
}

is_running() { # pidof is missing on old Android versions
  adb shell pidof "$PKG" > /dev/null 2>&1 && return 0
  adb shell ps 2>/dev/null | tr -d '\r' | grep -q " $PKG$"
}

screenshot() {
  adb shell screencap -p /sdcard/screen.png && adb pull /sdcard/screen.png "$OUT/$1.png" > /dev/null 2>&1 || true
}

adb wait-for-device
adb shell 'while [ "$(getprop sys.boot_completed | tr -d "\r")" != "1" ]; do sleep 2; done'
adb logcat -c || true

# ---------- 1. Update test ----------
if [ -n "$OLD_APK" ] && [ -f "$OLD_APK" ]; then
  # If the signing key changed (the Nov 2026 key retirement), Android can't update the old app:
  # people uninstall once. Then there is nothing to test until the next release.
  certs() { "$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)/apksigner" verify --print-certs "$1" 2>/dev/null | grep -i "certificate SHA-256" | sort -u; }
  if [ -n "${ANDROID_HOME:-}" ] && [ "$(certs "$OLD_APK")" != "$(certs "$APK")" ]; then
    note notice "Update test" "The last release is signed with a different key than this build (key retired in Nov 2026), so it can't be updated in place; update test skipped until the next release."
    OLD_APK=""
  fi
fi
if [ -n "$OLD_APK" ] && [ -f "$OLD_APK" ]; then
  echo "Installing the last release: $(basename "$OLD_APK")"
  if adb install -g "$OLD_APK"; then
    adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 > /dev/null
    sleep 15
    adb shell am force-stop "$PKG"
    before="$(first_install_time)"
    echo "Installing the new build over it"
    if ! adb install -r -g "$APK" > "$OUT/update-install.txt" 2>&1; then
      note error "Update test" "The new build wouldn't install over the last release: $(cat "$OUT/update-install.txt")"
      FAILED=1
    else
      after="$(first_install_time)"
      if [ -n "$before" ] && [ "$before" = "$after" ]; then
        echo "PASS: updated over the last release and the app's data was kept (first installed $before)."
      else
        note error "Update test" "The app was reinstalled instead of updated (first install time $before -> $after), so data would be lost."
        FAILED=1
      fi
    fi
  else
    note warning "Update test" "Couldn't install the last release on this Android version; update test skipped."
    adb install -r -g "$APK"
  fi
else
  echo "No last release given; installing the new build fresh."
  adb install -r -g "$APK"
fi

# ---------- 2. Start-up test ----------
adb logcat -c || true
echo "Opening $PKG"
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 > /dev/null
sleep 25
adb shell dumpsys activity activities > "$OUT/activities.txt" 2>&1 || true
screenshot start
crash_lines
if is_running && [ ! -s "$OUT/crash.txt" ]; then
  echo "PASS: $PKG is running and no crash was logged."
else
  summary="$( (cat "$OUT/crash.txt"; echo; grep -E "AndroidRuntime|$PKG" "$OUT/logcat.txt" | tail -n 40) | head -c 3500)"
  [ -z "$summary" ] && summary="No crash lines found. The app is not running."
  note error "UniFetcher crashed on the emulator" "$summary"
  echo "----- crash log -----"; cat "$OUT/crash.txt"
  exit 1
fi

# ---------- 3 + 4. Download tests ----------
# Shares <url> to UniFetcher, taps Download, waits for a new file whose name contains <match>.
download_test() {
  local name="$1" url="$2" match="$3" timeout="$4"
  echo "Download test ($name): $url"
  adb shell am force-stop "$PKG"
  adb shell am start -a android.intent.action.SEND -t text/plain \
    --es android.intent.extra.TEXT "'$url'" -n "$PKG/.QuickDownloadActivity" > /dev/null
  # Wait for the download sheet, then tap its Download button (the lowest "Download" text on screen).
  # In "Configure before download" mode the sheet shows Continue first, then the format screen's button.
  local tapped=0 continued=0 picked=""
  for _ in $(seq 1 20); do
    sleep 3
    adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 || continue
    adb pull /sdcard/ui.xml "$OUT/ui-$name.xml" > /dev/null 2>&1 || continue
    local found
    found="$(python3 - "$OUT/ui-$name.xml" "$continued" "$picked" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding="utf-8", errors="ignore").read()
def lowest(label):
    best = None
    for m in re.finditer(r'<node [^>]*?text="%s"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"' % re.escape(label), xml):
        x1, y1, x2, y2 = map(int, m.groups())
        if best is None or y1 > best[1]:
            best = ((x1 + x2) // 2, (y1 + y2) // 2)
    return best
# Preset gives a direct Download button; on the format screen, Suggested makes its Download button appear
# "X isn't responding" pop-ups from the emulator itself: tap Wait
for kind, label in (("dismiss", "Wait"), ("download", "Download"), ("download", "Start"), ("pick", "Preset"), ("pick", "Suggested"), ("continue", "Continue")):
    if kind == "continue" and int(sys.argv[2]) >= 2:
        continue
    if kind == "pick" and label in sys.argv[3].split(","):
        continue
    b = lowest(label)
    if b:
        print(kind, b[0], b[1], label)
        break
PY
)"
    if [ -n "$found" ]; then
      set -- $found
      adb shell input tap "$2" "$3"
      if [ "$1" = download ]; then tapped=1; break; fi
      if [ "$1" = dismiss ]; then echo "Dismissed a not-responding pop-up"; continue; fi
      if [ "$1" = pick ]; then picked="$picked,$4"; echo "Picked $4"; continue; fi
      continued=$((continued + 1)); echo "Tapped Continue ($continued)"; continue
    fi
    # The button can be below the bottom of a small screen: scroll the sheet up
    size="$(adb shell wm size | tr -d '\r' | grep -o '[0-9]*x[0-9]*' | tail -n 1)"
    w="${size%x*}"; h="${size#*x}"
    if [ -n "$w" ] && [ -n "$h" ]; then
      adb shell input swipe $((w / 2)) $((h * 85 / 100)) $((w / 2)) $((h * 25 / 100)) 400
    fi
  done
  screenshot "sheet-$name"
  if [ "$tapped" != 1 ]; then
    echo "No Download button seen for $name (it may have started straight away); waiting for the file."
  fi
  for _ in $(seq 1 $((timeout / 5))); do
    sleep 5
    local f
    f="$(adb shell ls "$DL" 2>/dev/null | tr -d '\r' | grep -i "$match" | grep -v -E '\.part$|\.ytdl$|\.temp' | head -n 1)"
    if [ -n "$f" ]; then
      sleep 3
      adb pull "$DL/$f" "$OUT/$f" > /dev/null 2>&1
      echo "Downloaded: $f ($(stat -c %s "$OUT/$f" 2>/dev/null) bytes)"
      DOWNLOADED="$OUT/$f"
      return 0
    fi
  done
  screenshot "after-$name"
  echo "No file for $name after ${timeout}s."
  # What was on screen and on disk, as a note on the run's summary (the full logs are an artifact)
  adb shell uiautomator dump /sdcard/ui2.xml > /dev/null 2>&1 && adb pull /sdcard/ui2.xml "$OUT/ui-after-$name.xml" > /dev/null 2>&1
  local texts files lines
  texts="$(grep -o 'text="[^"]\+"' "$OUT/ui-$name.xml" 2>/dev/null | sort -u | head -n 25 | tr '\n' ' ')"
  texts2="$(grep -o 'text="[^"]\+"' "$OUT/ui-after-$name.xml" 2>/dev/null | sort -u | head -n 25 | tr '\n' ' ')"
  files="$(adb shell ls -R /sdcard/Download 2>&1 | tr -d '\r' | head -n 20 | tr '\n' ' ')"
  adb logcat -d > "$OUT/logcat.txt" 2>/dev/null
  lines="$(grep -E "DownloadUtil|DownloaderV2|YoutubeDL|yt-dlp|QuickDownload|SiteSessions|StreamFinder" "$OUT/logcat.txt" | tail -n 12)"
  note warning "Download test details ($name)" "Tapped Download: $tapped. Screen before: $texts. Screen after: $texts2. Files: $files. Log: $lines"
  return 1
}

DOWNLOADED=""
if download_test local "http://10.0.2.2:8000/unifetcher-smoke-test.mp4" "unifetcher-smoke-test" 180; then
  probe="$(ffprobe -v error -show_entries stream=codec_type,codec_name,width,height -show_entries format=duration -of compact "$DOWNLOADED" 2>&1)"
  echo "$probe"
  if echo "$probe" | grep -q "codec_name=h264|codec_type=video|width=640|height=360" && echo "$probe" | grep -q "codec_name=aac|codec_type=audio"; then
    echo "PASS: the test video downloaded, has video and audio, and wasn't re-encoded."
  else
    note error "Download test" "The test video downloaded but isn't the same as the original (re-encoded or missing a stream): $probe"
    FAILED=1
  fi
else
  crash_lines
  note error "Download test" "Sharing a test video to UniFetcher didn't produce a downloaded file. See the screenshots in smoke-test-logs. $(grep -E "$PKG|yt-dlp|DownloadUtil" "$OUT/logcat.txt" | tail -n 15)"
  FAILED=1
fi

DOWNLOADED=""
if download_test commons "https://commons.wikimedia.org/wiki/File:Example.ogg" "example" 180; then
  if ffprobe -v error -show_entries stream=codec_type -of csv=p=0 "$DOWNLOADED" | grep -q audio; then
    echo "PASS: a real freely licensed file from Wikimedia Commons downloaded and plays."
  else
    note error "Real-site test" "The Wikimedia Commons file downloaded but has no audio."
    FAILED=1
  fi
else
  level=warning
  [ "$STRICT_REAL" = "true" ] && level=error && FAILED=1
  note "$level" "Real-site test" "Downloading a freely licensed file from Wikimedia Commons failed (the site may be down, or yt-dlp needs an update)."
fi

crash_lines
if [ -s "$OUT/crash.txt" ]; then
  note error "UniFetcher crashed during the download tests" "$(head -c 3000 "$OUT/crash.txt")"
  FAILED=1
fi
exit $FAILED
