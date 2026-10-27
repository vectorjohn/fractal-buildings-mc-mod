#!/usr/bin/env nix-shell
#! nix-shell -i python3 -p "python3.withPackages (ps: [ ps.numpy ps.pillow ])"
"""Read the text out of Minecraft screenshots and print JSON: [{"file": ..., "text": ...}].

Why not Tesseract: it garbles Minecraft's pixel font (digits especially, so coordinates come
out wrong). The game draws text from a fixed bitmap font at an integer GUI scale, so this
reads it exactly: it recovers the GUI scale, shrinks the white text back to native pixels
and matches glyphs against the game's own ascii.png.

Needs ORIGINAL screenshots (the pixel-exact PNGs), not resized/JPEG copies.
Reads white text (chat, F3) plus the green /seed number. Skips the hotbar item counts.

usage: ocr-screenshots.py [--jar MINECRAFT.jar | --font ascii.png] FILE_OR_DIR...
The font comes from --font, else from the Minecraft jar (--jar, $MC_JAR, or a
loom-cache clientOnly jar found under the current directory or its parents).
"""
import argparse, io, json, os, sys, zipfile
from collections import Counter
from pathlib import Path

import numpy as np
from PIL import Image

FONT_ENTRY = "assets/minecraft/textures/font/ascii.png"
CELL = 8
WHITE = 215  # a pixel counts as text when every channel is at least this bright
GREEN = (0x55, 0xFF, 0x55)  # chat draws the /seed number (a click-to-copy link) in this colour
GAP_SPACE = 4  # advance of ' ' in native pixels (glyph advance = width + 1)


def find_jar():
    if os.environ.get("MC_JAR"):
        return Path(os.environ["MC_JAR"])
    for d in [Path.cwd(), *Path.cwd().parents]:
        hits = sorted(d.glob(".gradle/loom-cache/minecraftMaven/net/minecraft/*/*/*clientOnly*.jar"))
        if hits:
            return hits[0]
    sys.exit("no Minecraft jar found; pass --jar or --font")


def load_glyphs(font_path, jar):
    if font_path:
        sheet = Image.open(font_path)
    else:
        with zipfile.ZipFile(jar or find_jar()) as z:
            sheet = Image.open(io.BytesIO(z.read(FONT_ENTRY)))
    a = np.asarray(sheet.convert("RGBA"))[:, :, 3] > 0
    cell = a.shape[0] // 16
    assert cell == CELL, f"unexpected font cell size {cell}"
    glyphs = []  # (char, bitmap[8,w])
    for code in range(0x21, 0x7F):
        r, c = divmod(code, 16)
        g = a[r * CELL:(r + 1) * CELL, c * CELL:(c + 1) * CELL]
        cols = np.where(g.any(axis=0))[0]
        if len(cols):
            glyphs.append((chr(code), g[:, :cols[-1] + 1]))
    # Index by first column so a position only tries the few glyphs that could start there.
    by_first = {}
    for ch, g in glyphs:
        by_first.setdefault(g[:, 0].tobytes(), []).append((ch, g))
    # Prefer the glyph with the most ink when several fit (e.g. a narrow glyph inside a wide one).
    for v in by_first.values():
        v.sort(key=lambda t: -int(t[1].sum()))
    return by_first


def parse_row(band, x_from, by_first):
    """Greedy left-to-right parse of one text line (an 8-row band), starting at column x_from.
    Returns (text, ink_matched, x_start, x_end)."""
    W = band.shape[1]
    colink = band.any(axis=0)
    x, out, ink, first, last_end = x_from, [], 0, None, x_from
    while x < W:
        if not colink[x]:
            x += 1
            continue
        hit = None
        for ch, g in by_first.get(band[:, x].tobytes(), ()):
            w = g.shape[1]
            if x + w > W:
                continue
            if np.array_equal(band[:, x:x + w], g) and (x + w >= W or not colink[x + w]):
                hit = (ch, w, int(g.sum()))
                break
        if hit is None:
            # unmatched ink: stop this run of text (not a glyph we know)
            if out:
                break
            x += 1
            continue
        ch, w, n = hit
        if first is None:
            first = x
        else:
            empty = x - last_end - 1  # empty columns between this glyph and the last (1 is normal)
            out.append(" " * max(0, round((empty - 1) / GAP_SPACE)) if empty > 1 else "")
        out.append(ch)
        ink += n
        last_end = x + w
        x += w
    return "".join(out), ink, first or 0, last_end


def read_lines(m, by_first):
    """All text lines in a native-resolution bool mask, as (y, x, text)."""
    # Parse at every row, and keep the best-scoring parse among overlapping ones.
    # A line may continue after unmatched ink, so re-parse from each unmatched point.
    found = []
    H, W = m.shape
    for y0 in range(0, H - CELL + 1):
        band = m[y0:y0 + CELL]
        if not band.any():
            continue
        x_from = 0
        while x_from < W:
            text, ink, x0, x1 = parse_row(band, x_from, by_first)
            if sum(c.isalnum() for c in text) >= 2:  # drops specks from bright scenery
                found.append((y0, x0, text, ink))
            if x1 <= x_from:
                break
            x_from = x1 + 1
    # Overlapping rows: keep highest ink, drop lower-scoring parses within CELL rows.
    found.sort(key=lambda t: -t[3])
    kept = []
    for y, x, t, ink in found:
        if any(abs(y - ky) < CELL and x < kx + 8 * len(kt) and kx < x + 8 * len(t)
               for ky, kx, kt in kept):
            continue
        kept.append((y, x, t))
    kept.sort()
    return kept


def text_mask(rgb):
    green = (np.abs(rgb.astype(int) - GREEN) <= 6).all(axis=2)
    return (rgb >= WHITE).all(axis=2) | green


def best_scale(rgb, by_first):
    white = text_mask(rgb)
    runs = Counter()
    for row in white[::7]:
        edges = np.diff(np.concatenate(([0], row.view(np.int8), [0])))
        starts, ends = np.where(edges == 1)[0], np.where(edges == -1)[0]
        runs.update((ends - starts).tolist())
    cands = [r for r, _ in runs.most_common(6) if 1 <= r <= 16]
    best = (0, None, None)
    for s in cands:
        m = white[s // 2::s, s // 2::s]
        lines = read_lines(m, by_first)
        score = sum(c.isalnum() for _, _, t in lines for c in t)
        if score > best[0]:
            best = (score, s, lines)
    return best[1], best[2] or []


def ocr(path, by_first):
    rgb = np.asarray(Image.open(path).convert("RGB"))
    s, lines = best_scale(rgb, by_first)
    if not s:
        return ""
    Hn, Wn = rgb.shape[0] // s, rgb.shape[1] // s
    keep = []
    for y, x, t in lines:
        # the hotbar's item counts ("64") sit bottom-centre
        if y > 0.9 * Hn and 0.28 * Wn < x < 0.72 * Wn:
            continue
        keep.append(t)
    return "\n".join(keep)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("paths", nargs="+")
    ap.add_argument("--jar")
    ap.add_argument("--font")
    args = ap.parse_args()
    by_first = load_glyphs(args.font, args.jar)
    files = []
    for p in map(Path, args.paths):
        files += sorted(p.glob("*.png")) if p.is_dir() else [p]
    out = [{"file": f.name, "text": ocr(f, by_first)} for f in files]
    json.dump(out, sys.stdout, indent=2, ensure_ascii=False)
    print()


if __name__ == "__main__":
    main()
