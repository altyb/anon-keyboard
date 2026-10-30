#!/usr/bin/env python3
"""Nile Birds brand: one flat bird per app, drawn from numbers on the 108 adaptive-icon grid.
Usage: birds.py <shoebill|hudhud> <repo root>   (needs rsvg-convert)"""
import math, pathlib, subprocess, sys

def capsule(cx, cy, angle, start, end, width):
    a = math.radians(angle); ux, uy = math.cos(a), math.sin(a); nx, ny = -uy, ux; r = width / 2
    p = lambda d, s: (cx + ux * d + nx * s, cy + uy * d + ny * s)
    (x1, y1), (x2, y2), (x3, y3), (x4, y4) = p(start, r), p(end, r), p(end, -r), p(start, -r)
    return f"M{x1:.1f},{y1:.1f} L{x2:.1f},{y2:.1f} A{r},{r} 0 0 0 {x3:.1f},{y3:.1f} L{x4:.1f},{y4:.1f} Z"

ANG = (-162, -131, -100, -69, -38)
BIRDS = {
    # Shoebill: huge clog of a bill, and an eye that is a play button.
    "shoebill": dict(bg="#17252E", fg="#C5D5DE", body=(
        "M26,86 C28,74 27,66 25,56 L18,48 L26,46 C28,35 37,29 47,30 C53,30 57,32 60,35 L84,49 "
        "C89,52 90,58 87,66 C85,62 83,60 80,60 C72,72 60,76 50,71 C48,76 49,81 52,86 Z "
        "M36,39 L36,50 L46,44.5 Z "                     # the eye
        "M52,56 C61,59 70,60 79,58 L79,60 C70,62.5 61,61.5 52,58.5 Z")),   # the bill line
    # Hudhud (hoopoe): fan crest of rounded feathers, tips cut off like keys, long thin bill.
    "hudhud": dict(bg="#0F3A40", fg="#F2A65A", body=(
        "M33,84 C31,72 34,62 40,55 C46,48 57,47 63,53 C65,55 66,57 67,59 "
        "C73,60 79,62 84,65 L83,67 C77,66 71,66 65,67 C62,72 57,74 53,74 C52,78 53,81 55,84 Z "
        "M56,57 A3.2,3.2 0 1 0 56,57.01 Z"),
        crest=" ".join(capsule(49, 54, a, 8, 20, 8) for a in ANG),
        tips=" ".join(capsule(49, 54, a, 19.5, 26, 8) for a in ANG)),
}

def svg(b, size=108, square=False, mono=False):
    bg = f'<rect width="108" height="108" fill="{b["bg"]}"' + (' rx="24"' if square else '') + '/>' if not mono else ''
    fg = "#FFFFFF" if mono else b["fg"]
    tips = b.get("tips", "")
    t = f'<path fill="{fg}" d="{b["crest"]}"/><path fill="#FFFFFF" d="{tips}"/>' if tips else ''
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="{size}" height="{size}">{bg}'
            f'<path fill="{fg}" fill-rule="evenodd" d="{b["body"]}"/>{t}</svg>\n')

def vector(b, mono=False):
    fg = "#FFFFFFFF" if mono else b["fg"]
    t = f'\n    <path android:fillColor="#FFFFFFFF" android:pathData="{b["tips"]}"/>' if b.get("tips") else ''
    return ('<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n'
            f'    <path android:fillColor="{fg}" android:fillType="evenOdd" android:pathData="{b["body"]}"/>{t}\n</vector>\n')

name, root = sys.argv[1], pathlib.Path(sys.argv[2])
b = BIRDS[name]
out = root / "design/logo"; out.mkdir(parents=True, exist_ok=True)
(out / f"{name}.svg").write_text(svg(b, 512, square=True))
(out / f"{name}-mark.svg").write_text(svg(b, 512, mono=True))
res = root / "app/src/main/res"
if res.exists():
    (res / "drawable/ic_launcher_foreground.xml").write_text(vector(b))
    (res / "drawable/ic_launcher_monochrome.xml").write_text(vector(b, mono=True))
