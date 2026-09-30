"""Generate the K-Pulse launcher icon set (concept B — pulse tick).

Two artefact families, because minSdk is 24:
  * Adaptive icon (API 26+): vector foreground + solid background + monochrome
    layer for Android 13 themed icons.
  * Legacy PNG mipmaps (API 24-25): the composed icon, square and round.

The demo build type overrides both layers so the two apps are unmistakable in
the launcher: paper trace on marigold-deep, instead of marigold on ink.
"""
import os
from PIL import Image, ImageDraw

ROOT = "D:/project/voter/workspace/mobile/app/src"

INK = "#152A38"
PAPER = "#F3EEE3"
MARIGOLD = "#C98A2E"
MARIGOLD_DEEP = "#8F5F17"

SS = 8
DENSITIES = [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]


def rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


# ── Adaptive foreground geometry ────────────────────────────────────────────
# 108x108 viewport. The guaranteed-visible region is the central 72dp, so every
# point plus half the stroke must stay within radius 36 of (54,54). The round-1
# artwork overflowed that at the tick's tip; these coordinates are the same
# shape scaled 0.86 about the centre, which brings the worst point to 34.4.
ADAPTIVE_PATH = [
    (27.99, 54.00),
    (36.37, 54.00),
    (41.89, 61.43),
    (49.36, 33.57),
    (56.79, 70.72),
    (80.01, 39.14),
]
ADAPTIVE_STROKE = 8.82


def vector_drawable(color):
    d = "M" + " L".join(f"{x:.2f},{y:.2f}" for x, y in ADAPTIVE_PATH)
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!--
  K-Pulse mark: one continuous stroke that falls into a trough and rises out of
  it as a tick — a pulse reading and "recorded" in the same gesture.

  Sized so the whole path plus half its stroke width stays inside the central
  72dp of the 108dp canvas, which is the only region an adaptive icon is
  guaranteed to show whatever mask the launcher applies.
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:pathData="{d}"
        android:strokeColor="{color}"
        android:strokeWidth="{ADAPTIVE_STROKE}"
        android:strokeLineCap="round"
        android:strokeLineJoin="round" />
</vector>
"""


ADAPTIVE_XML = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
"""


def color_xml(value):
    return f"""<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">{value}</color>
</resources>
"""


# ── Legacy PNG rendering ────────────────────────────────────────────────────
# Legacy icons have no 108/72 inset, so the mark is drawn larger here than in
# the adaptive foreground.
LEGACY_PATH = [
    (0.22, 0.50), (0.31, 0.50), (0.37, 0.58),
    (0.45, 0.28), (0.53, 0.68), (0.78, 0.34),
]
LEGACY_STROKE = 0.095


def draw_icon(size, bg, fg, round_mask):
    s = size * SS
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if round_mask:
        d.ellipse([0, 0, s - 1, s - 1], fill=rgb(bg))
    else:
        d.rounded_rectangle([0, 0, s - 1, s - 1], radius=s * 0.225, fill=rgb(bg))

    pts = [(x * s, y * s) for x, y in LEGACY_PATH]
    w = int(s * LEGACY_STROKE)
    d.line(pts, fill=rgb(fg), width=w, joint="curve")
    r = w / 2
    for (x, y) in (pts[0], pts[-1]):
        d.ellipse([x - r, y - r, x + r, y + r], fill=rgb(fg))

    return img.resize((size, size), Image.LANCZOS)


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    print("  ", path.replace(ROOT, "…"))


def emit(source_set, bg, fg, mono_needed):
    base = f"{ROOT}/{source_set}/res"
    write(f"{base}/drawable/ic_launcher_foreground.xml", vector_drawable(fg))
    if mono_needed:
        # Monochrome layers are tinted by the system from their alpha, so the
        # colour here only has to be opaque.
        write(f"{base}/drawable/ic_launcher_monochrome.xml", vector_drawable("#FFFFFF"))
        write(f"{base}/mipmap-anydpi-v26/ic_launcher.xml", ADAPTIVE_XML)
        write(f"{base}/mipmap-anydpi-v26/ic_launcher_round.xml", ADAPTIVE_XML)
    write(f"{base}/values/ic_launcher_background.xml", color_xml(bg))

    for name, px in DENSITIES:
        for fname, rnd in (("ic_launcher.png", False), ("ic_launcher_round.png", True)):
            out = f"{base}/mipmap-{name}/{fname}"
            os.makedirs(os.path.dirname(out), exist_ok=True)
            draw_icon(px, bg, fg, rnd).save(out)
    print(f"   {len(DENSITIES) * 2} PNGs under {source_set}/res/mipmap-*")


if __name__ == "__main__":
    print("main (K-Pulse Field): marigold on ink")
    emit("main", INK, MARIGOLD, mono_needed=True)
    print("demo (K-Pulse Demo): paper on marigold-deep")
    emit("demo", MARIGOLD_DEEP, PAPER, mono_needed=False)
