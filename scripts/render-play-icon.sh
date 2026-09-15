#!/usr/bin/env bash
# Renders the Play Store listing icon from the same SVG the launcher icon comes from.
#
# The listing icon is NOT the adaptive icon. Play shows the whole square behind a corner
# radius of its own, so this is the source at its own proportions — none of the 72/108
# scaling or the remapped gradient stops that app/icon/README.md explains, both of which
# exist only because a launcher crops.
#
# Usage: scripts/render-play-icon.sh [output.png]
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
svg="$root/app/icon/splouch-icon-s-cutout.svg"
out="${1:-$root/app/icon/play-store-512.png}"
chrome="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
[ -x "$chrome" ] || { echo "Need Chrome at $chrome to rasterise the SVG." >&2; exit 1; }

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

# Rendered at 1024 and halved, so the droplet's curves and the S's corners come down
# with some resampling behind them rather than being sampled once at the final size.
{
  printf '<!doctype html><meta charset="utf-8"><style>html,body{margin:0;padding:0;overflow:hidden}svg{display:block;width:1024px;height:1024px}</style>\n'
  tail -n +2 "$svg"
} > "$tmp/icon.html"

"$chrome" --headless --disable-gpu --hide-scrollbars --force-device-scale-factor=1 \
  --window-size=1024,1024 --screenshot="$tmp/1024.png" "file://$tmp/icon.html" >/dev/null 2>&1

cp "$tmp/1024.png" "$tmp/512.png"
sips -z 512 512 "$tmp/512.png" >/dev/null

# Play's console validates a 32-bit PNG, and Chrome writes 24-bit for opaque art, so the
# alpha channel is added here. It is uniformly 255 — the format is what is being satisfied.
python3 - "$tmp/512.png" "$out" <<'PY'
import struct, sys, zlib

def read_png(p):
    d = open(p, 'rb').read(); assert d[:8] == b'\x89PNG\r\n\x1a\n'
    i, idat, w, h, bd, ct = 8, b'', 0, 0, 0, 0
    while i < len(d):
        ln = struct.unpack('>I', d[i:i+4])[0]; typ = d[i+4:i+8]; data = d[i+8:i+8+ln]; i += 12 + ln
        if typ == b'IHDR': w, h, bd, ct = struct.unpack('>IIBB', data[:10])
        elif typ == b'IDAT': idat += data
    assert bd == 8 and ct in (2, 6), f'unexpected {bd}/{ct}'
    ch = 3 if ct == 2 else 4
    raw = zlib.decompress(idat); stride = w * ch
    out, prev, pos = bytearray(), bytearray(stride), 0
    for _ in range(h):
        f = raw[pos]; pos += 1
        line = bytearray(raw[pos:pos+stride]); pos += stride
        if f:
            for x in range(stride):
                a = line[x-ch] if x >= ch else 0
                b = prev[x]
                c = prev[x-ch] if x >= ch else 0
                if f == 1: line[x] = (line[x] + a) & 255
                elif f == 2: line[x] = (line[x] + b) & 255
                elif f == 3: line[x] = (line[x] + (a + b) // 2) & 255
                else:
                    pp = a + b - c; pa, pb, pc = abs(pp-a), abs(pp-b), abs(pp-c)
                    line[x] = (line[x] + (a if (pa <= pb and pa <= pc) else (b if pb <= pc else c))) & 255
        out += line; prev = line
    return w, h, ch, bytes(out)

src, dst = sys.argv[1], sys.argv[2]
w, h, ch, px = read_png(src)
assert (w, h) == (512, 512), (w, h)
if ch == 3:
    rgba = bytearray(w * h * 4)
    for i in range(w * h):
        rgba[i*4:i*4+3] = px[i*3:i*3+3]
        rgba[i*4+3] = 255
    px = bytes(rgba)

def chunk(t, d):
    return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)

raw = b''.join(b'\x00' + px[y*w*4:(y+1)*w*4] for y in range(h))
open(dst, 'wb').write(b'\x89PNG\r\n\x1a\n'
                      + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
                      + chunk(b'IDAT', zlib.compress(raw, 9))
                      + chunk(b'IEND', b''))
PY

echo "$out"
sips -g pixelWidth -g pixelHeight -g hasAlpha "$out" | tail -3
