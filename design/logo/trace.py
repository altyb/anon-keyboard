"""PNG logo -> clean SVG: snap to a few flat colours, make the outside transparent, trace with vtracer.
Usage: python trace.py gemini-1.png logo.svg"""
import sys
from PIL import Image, ImageDraw
import vtracer

PALETTE = {  # snapped colours (measured from the Gemini image)
    "charcoal": (0x1C, 0x1D, 0x20), "white": (0xFF, 0xFF, 0xFF), "bevel": (0xE4, 0xE5, 0xE9),
    "shadow": (0xA6, 0xA8, 0xAD), "cyan": (0x3D, 0xB8, 0xE6),
}
src, out = sys.argv[1], sys.argv[2]
im = Image.open(src).convert("RGB")
cols = list(PALETTE.values())
snap = Image.new("RGB", im.size)
px, sp = im.load(), snap.load()
for y in range(im.height):
    for x in range(im.width):
        r, g, b = px[x, y]
        sp[x, y] = min(cols, key=lambda c: (c[0]-r)**2 + (c[1]-g)**2 + (c[2]-b)**2)
ImageDraw.floodfill(snap, (0, 0), (255, 0, 255))  # outside white -> key colour for transparency
rgba = snap.convert("RGBA")
rgba.putdata([(0, 0, 0, 0) if p[:3] == (255, 0, 255) else p for p in rgba.getdata()])
tmp = out + ".png"
rgba.save(tmp)
vtracer.convert_image_to_svg_py(tmp, out, colormode="color", hierarchical="stacked", mode="spline",
                                filter_speckle=8, color_precision=8, layer_difference=8,
                                corner_threshold=60, length_threshold=4.0, splice_threshold=45, path_precision=2)
