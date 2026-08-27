#!/bin/zsh
# Record the sample app on a connected device and turn it into a README GIF.
# Usage: docs/tools/record-gif.sh <name> [seconds]   (default 12 s)
# Output: docs/images/<name>.mp4 and docs/images/<name>.gif (720 px wide, 24 fps, palette-optimised).
# Needs ffmpeg (`brew install ffmpeg`). Start the interaction after the "recording" line.
set -eu
name=${1:?name}; secs=${2:-12}
adb=${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb
out=$(dirname "$0")/../images; mkdir -p "$out"
echo "recording ${secs}s… perform the gesture now"
$adb shell screenrecord --time-limit "$secs" --bit-rate 8000000 /sdcard/popupbar-"$name".mp4
$adb pull /sdcard/popupbar-"$name".mp4 "$out/$name.mp4" >/dev/null
$adb shell rm /sdcard/popupbar-"$name".mp4
ffmpeg -y -loglevel error -i "$out/$name.mp4" -vf "fps=24,scale=720:-1:flags=lanczos,palettegen=stats_mode=diff" "$out/$name-palette.png"
ffmpeg -y -loglevel error -i "$out/$name.mp4" -i "$out/$name-palette.png" -lavfi "fps=24,scale=720:-1:flags=lanczos[x];[x][1:v]paletteuse=dither=bayer:bayer_scale=5" "$out/$name.gif"
rm -f "$out/$name-palette.png"
ls -la "$out/$name.gif" | awk '{print "gif:", $5/1024 " KB"}'
