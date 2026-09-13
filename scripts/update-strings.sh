#!/bin/zsh
# Regenerates the built-in string snapshot (app.md T-10) from a running server.
#
#   scripts/update-strings.sh https://<default cloud>
#
# Fetches GET /locales, then GET /i18n/{lang} for each language it lists, and writes each
# body verbatim to core/src/main/resources/i18n/<lang>.json, plus the /locales body as
# locales.json. Run it before a release and whenever the server's shared/locales/
# changes. Never edit the files by hand: the server is the source, this is a captured floor.
set -euo pipefail
BASE=${1:?usage: update-strings.sh <server base url>}
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT=$ROOT/core/src/main/resources/i18n
BASE=${BASE%/}
mkdir -p "$OUT"

curl -sfS "$BASE/locales" -o "$OUT/locales.json"
codes=$(python3 -c 'import json,sys; print(" ".join(sorted(e["code"] for e in json.load(open(sys.argv[1])))))' "$OUT/locales.json")
[[ -n "$codes" ]] || { echo "no locales from $BASE" >&2; exit 1 }

for f in "$OUT"/*.json(N); do [[ $(basename "$f") == locales.json ]] || rm -f "$f"; done
for code in ${=codes}; do
    curl -sfS "$BASE/i18n/$code" -o "$OUT/$code.json"
    python3 -c "import json; json.load(open('$OUT/$code.json'))"   # must be JSON
done
echo "wrote $OUT for: $codes (from $BASE on $(date -u +%Y-%m-%dT%H:%MZ))"
