#!/usr/bin/env python3
"""Build assets/emoji-names.txt: names for the emoji in emoji.txt, from Unicode CLDR annotations.
Usage: tools/emoji-names.py <cldr en.xml> <cldr en-derived.xml> <cldr ar.xml> <cldr ar-derived.xml>"""
import sys, xml.etree.ElementTree as ET

def names(path):
    out = {}
    for a in ET.parse(path).getroot().iter('annotation'):
        if a.get('type') != 'tts':
            out.setdefault(a.get('cp').replace('️', ''), a.text.replace('|', ' '))
    return out

en, ar = {}, {}
for p in sys.argv[1:3]: en.update(names(p))
for p in sys.argv[3:5]: ar.update(names(p))
emoji = []
for line in open('app/src/main/assets/emoji.txt', encoding='utf8'):
    if line.strip():
        emoji += [e for e in line.rstrip('\n').split('\t')[1].split(' ') if e]
missing = 0
with open('app/src/main/assets/emoji-names.txt', 'w', encoding='utf8') as f:
    for e in emoji:
        k = e.replace('️', '')
        if k not in en: missing += 1; continue
        words = lambda s: ' '.join(s.replace(':', ' ').replace(',', ' ').lower().split())
        f.write(f"{e}\t{words(en[k])}\t{words(ar.get(k, ''))}\n")
print(f'{len(emoji)} emoji, {missing} without names')
