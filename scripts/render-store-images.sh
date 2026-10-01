#!/usr/bin/env bash
# Renders the Google Play listing graphics from the screenshots in screenshots/ and the
# icon SVG, per language:
#
#   store/<lang>/feature-graphic.png   1024x500, the banner above the listing
#   store/<lang>/phone-<n>.png         1080x1920, a captioned screenshot
#
# The raw captures are 1080x2400 (20:9). Play refuses a screenshot whose long side is
# more than twice its short side, so they are set into a 9:16 frame with a caption
# rather than uploaded as they are. See store/README.md.
#
# Usage: scripts/render-store-images.sh
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
chrome="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
[ -x "$chrome" ] || { echo "Need Chrome at $chrome to render the pages." >&2; exit 1; }

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

# The icon without its XML prolog, to inline in the pages.
tail -n +2 "$root/app/icon/splouch-icon-s-cutout.svg" > "$tmp/icon.svg"

render() { # html width height out
  "$chrome" --headless --disable-gpu --hide-scrollbars --force-device-scale-factor=1 \
    --allow-file-access-from-files --window-size="$2,$3" --screenshot="$4" "file://$1" >/dev/null 2>&1
}

style='html,body{margin:0;padding:0;height:100%;overflow:hidden}
body{font-family:-apple-system,"Helvetica Neue",Helvetica,Arial,sans-serif;color:#fff;
  background:linear-gradient(180deg,#093b86 0%,#1379ce 55%,#2ac3ea 100%)}'

# lang | n | screenshot | caption
shots='en|1|dark/1-scoreboard|Live times, lane by lane
en|2|light/2-results|Results as soon as the heat ends
en|3|dark/3-schedule|Every heat of the meet
en|4|light/1-scoreboard|Light or dark, your choice
fr|1|dark/1-scoreboard|Les temps en direct, couloir par couloir
fr|2|light/2-results|Les résultats dès la fin de la série
fr|3|dark/3-schedule|Toutes les séries de la compétition
fr|4|light/1-scoreboard|Clair ou sombre, à vous de choisir'

while IFS='|' read -r lang n shot caption; do
  mkdir -p "$root/store/$lang"
  page="$tmp/$lang-$n.html"
  cat > "$page" <<HTML
<!doctype html><meta charset="utf-8"><style>$style
.cap{position:absolute;top:0;left:0;right:0;height:300px;display:flex;align-items:center;
  justify-content:center;text-align:center;padding:0 90px;box-sizing:border-box;
  font-size:66px;font-weight:700;line-height:1.15;letter-spacing:-0.5px;text-wrap:balance}
img{position:absolute;top:300px;left:50%;transform:translateX(-50%);height:1560px;
  border-radius:44px;box-shadow:0 24px 60px rgba(0,0,0,.35)}
</style><div class="cap">$caption</div><img src="file://$root/screenshots/$lang/$shot.png">
HTML
  render "$page" 1080 1920 "$root/store/$lang/phone-$n.png"
done <<< "$shots"

# lang | tagline
banners='en|Live scores from the pool, on your phone
fr|Les résultats de la piscine, en direct sur votre téléphone'

while IFS='|' read -r lang tagline; do
  page="$tmp/$lang-feature.html"
  cat > "$page" <<HTML
<!doctype html><meta charset="utf-8"><style>$style
body{display:flex;align-items:center;gap:56px;padding:0 80px;height:500px;box-sizing:border-box}
svg{width:260px;height:260px;flex:none;border-radius:58px;box-shadow:0 16px 40px rgba(0,0,0,.3)}
h1{margin:0;font-size:104px;font-weight:800;letter-spacing:-2px}
p{margin:12px 0 0;font-size:40px;font-weight:500;line-height:1.2;opacity:.95;text-wrap:balance}
</style>$(cat "$tmp/icon.svg")<div><h1>Splouch</h1><p>$tagline</p></div>
HTML
  render "$page" 1024 500 "$root/store/$lang/feature-graphic.png"
done <<< "$banners"

for f in "$root"/store/*/*.png; do
  printf '%s  ' "${f#$root/}"
  sips -g pixelWidth -g pixelHeight "$f" | tail -2 | awk '{printf "%s ", $2}'; echo
done
