#!/usr/bin/env bash
# Installs the APK on the emulator, walks the main screens, records screenshots + API logs + crashes.
set -u
OUT=smoke
mkdir -p "$OUT"
APK=$(ls out/*.apk | head -1)
PKG=net.kravos.sports

adb install -r "$APK" > "$OUT/install.txt" 2>&1
adb logcat -c
adb shell am start -W -n $PKG/.ui.MainActivity > "$OUT/start.txt" 2>&1
sleep 25

shot() { adb exec-out screencap -p > "$OUT/$1.png"; echo "shot $1" >> "$OUT/steps.txt"; }

dump() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null 2>&1
}

# tap_text "<exact text>" [index]
tap_text() {
  dump
  python3 - "$1" "${2:-0}" "$OUT/ui.xml" <<'PY' > "$OUT/tap.txt"
import re, sys, xml.etree.ElementTree as ET
want, idx, path = sys.argv[1], int(sys.argv[2]), sys.argv[3]
try:
    root = ET.parse(path).getroot()
except Exception:
    sys.exit(0)
hits = []
for n in root.iter('node'):
    if n.get('text') == want or n.get('content-desc') == want:
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
        hits.append(((x1 + x2) // 2, (y1 + y2) // 2))
if len(hits) > idx:
    print(*hits[idx])
PY
  if [ -s "$OUT/tap.txt" ]; then
    adb shell input tap $(cat "$OUT/tap.txt")
    echo "tapped $1" >> "$OUT/steps.txt"
  else
    echo "not found: $1" >> "$OUT/steps.txt"
  fi
  sleep 3
}

# tap the first node whose text matches a regex (e.g. a score row clock)
tap_regex() {
  dump
  python3 - "$1" "$OUT/ui.xml" <<'PY' > "$OUT/tap.txt"
import re, sys, xml.etree.ElementTree as ET
pat, path = re.compile(sys.argv[1]), sys.argv[2]
try:
    root = ET.parse(path).getroot()
except Exception:
    sys.exit(0)
for n in root.iter('node'):
    t = n.get('text') or ''
    if pat.fullmatch(t):
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
        if y1 > 500:
            print((x1 + x2) // 2 + 200, (y1 + y2) // 2)
            break
PY
  if [ -s "$OUT/tap.txt" ]; then adb shell input tap $(cat "$OUT/tap.txt"); echo "tapped /$1/" >> "$OUT/steps.txt"; else echo "not found /$1/" >> "$OUT/steps.txt"; fi
  sleep 4
}

SIZE=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1)
W=${SIZE%x*}; H=${SIZE#*x}
scroll() { adb shell input swipe $((W / 2)) $((H * 3 / 4)) $((W / 2)) $((H / 4)) 400; sleep 2; }

shot 01-scores
scroll; shot 02-scores-scroll
tap_text "UTO"             # yesterday: finished matches
sleep 6
shot 03-yesterday
tap_text "Kraj"            # a finished match row
sleep 6
shot 10-match
scroll; shot 11-match-scroll
scroll; shot 12-match-scroll2
tap_text "Statistika"; sleep 4; shot 13-stats
tap_text "Postave"; sleep 4; shot 14-lineups
scroll; shot 15-lineups-scroll
tap_text "Heatmapa"; sleep 12; shot 16-heat
scroll; shot 17-heat-scroll
scroll; shot 18-heat-scroll2
tap_text "Šutevi"; sleep 4; shot 185-shots
tap_text "Tabela"; sleep 4; shot 19-table
tap_text "Međusobni"; sleep 5; shot 20-h2h
adb shell input keyevent KEYCODE_BACK; sleep 2

tap_text "UŽIVO" ; sleep 6; shot 21-live
tap_text "UŽIVO" ; sleep 2
tap_text "Košarka"; sleep 6; shot 30-basketball
tap_text "Kraj"; sleep 6; shot 31-bb-match
tap_text "Igrači"; sleep 4; shot 32-bb-players
adb shell input keyevent KEYCODE_BACK; sleep 2
tap_text "Tenis"; sleep 6; shot 33-tennis
tap_text "Formula 1"; sleep 6; shot 34-f1
tap_text "Fudbal"; sleep 4

tap_text "Lige"; sleep 5; shot 40-leagues
tap_text "Premier League"; sleep 6; shot 41-pl-table
tap_text "Igrači"; sleep 6; shot 42-pl-players
adb shell input keyevent KEYCODE_BACK; sleep 2

tap_text "Pretraga"; sleep 2
tap_text "Tim, liga ili igrač…"
adb shell input text "Partizan"
sleep 7
shot 50-search
tap_text "Omiljeno" 1; sleep 2
shot 51-search-star
tap_text "Omiljeno" 0; sleep 8
shot 52-favorites

tap_text "Rezultati"; sleep 2
tap_text "Podešavanja"; sleep 3; shot 60-settings

PID=$(adb shell pidof $PKG | tr -d '\r')
echo "pid=$PID" > "$OUT/status.txt"
adb logcat -d > "$OUT/logcat.txt"
grep -n -A 40 "FATAL EXCEPTION" "$OUT/logcat.txt" > "$OUT/crash.txt" || true
grep -n -E "KravosSports|AndroidRuntime|net.kravos" "$OUT/logcat.txt" | tail -500 > "$OUT/app-log.txt" || true
if [ -s "$OUT/crash.txt" ]; then echo "CRASH" >> "$OUT/status.txt"; fi
rm -f "$OUT/logcat.txt" "$OUT/ui.xml"
cat "$OUT/status.txt" "$OUT/steps.txt"
exit 0
