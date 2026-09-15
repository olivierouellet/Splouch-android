# App icon

`splouch-icon-s-cutout.svg` is the source, 1024×1024. Everything under
`src/main/res` is derived from it and should be regenerated rather than edited:

| Derived file | Is |
| --- | --- |
| `drawable/ic_launcher_background.xml` | the gradient plate and its highlight |
| `drawable/ic_launcher_foreground.xml` | the droplet and the three bubbles |
| `drawable/ic_launcher_monochrome.xml` | the same geometry flat, for Android 13+ themed icons |
| `mipmap-anydpi-v26/ic_launcher.xml` | the three layers together |

Two things are not a straight copy of the SVG, and both follow from an adaptive icon
being 108dp of which a launcher only ever shows the middle 72dp:

- **The foreground is scaled by 2/3** (72/108) about the centre, so a square drawn to
  fill itself ends up filling the part that is actually shown. At 1:1 the droplet would
  be half again too big and would run into the mask.
- **The background's gradient stops span 171..853**, not 0..1024 — the same 72dp window —
  so the dark top and the bright bottom arrive inside the mask instead of being cropped
  off with the corners. The plate itself still bleeds to all four edges, which is what
  the outer ring is for.

`minSdk` is 26, so `mipmap-anydpi-v26` covers every supported device and there are no
PNG densities to keep in step. A Play Store listing still wants its own 512×512 PNG;
that is not a build input and is not kept here.
