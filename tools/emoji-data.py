#!/usr/bin/env python3
"""Build assets/emoji.txt and emoji-tones.txt from Unicode's emoji-test.txt.
Usage: tools/emoji-data.py <emoji-test.txt>. Run tools/emoji-names.py afterwards."""
import sys
TABS = [("😀", ["Smileys & Emotion"]), ("👋", ["People & Body"]), ("🐶", ["Animals & Nature"]), ("🍔", ["Food & Drink"]),
        ("⚽", ["Activities"]), ("🚗", ["Travel & Places"]), ("💡", ["Objects"]), ("❤️", ["Symbols", "Flags"])]
groups, group, full = {}, None, set()
for line in open(sys.argv[1], encoding="utf8"):
    if line.startswith("# group: "):
        group = line[9:].strip()
    elif "; fully-qualified" in line:
        e = "".join(chr(int(c, 16)) for c in line.split(";")[0].split())
        full.add(e)
        if not any(0x1F3FB <= ord(c) <= 0x1F3FF for c in e):
            groups.setdefault(group, []).append(e)
with open("app/src/main/assets/emoji.txt", "w", encoding="utf8") as f:
    for icon, names in TABS:
        f.write(icon + "\t" + " ".join(e for n in names for e in groups[n]) + "\n")
# A tone goes right after the first code point; keep only emoji where Unicode lists exactly that form.
tones = [e for es in groups.values() for e in es if e[0] + "\U0001F3FB" + e[1:].replace("️", "", 1) in full
         or e[0] + "\U0001F3FB" + e[1:] in full]
open("app/src/main/assets/emoji-tones.txt", "w", encoding="utf8").write(" ".join(tones))
print(sum(len(v) for v in groups.values()), "emoji,", len(tones), "with tones")
