"""Remove offensive words from the bundled word lists, so the keyboard never suggests them.
Lists: dict-src/blocked/<lang>.txt, one word per line, reviewed by hand. Exact matches only, because
substring matching would also hit words like "grapes". Safe to run again after a word list changes.
Usage: python tools/filter-words.py"""
import os

assets = "app/src/main/assets"
for lang in ("en", "ar", "de", "es", "fr", "pt", "ru"):
    with open(f"dict-src/blocked/{lang}.txt", encoding="utf8") as f:
        blocked = {l.strip().lower() for l in f if l.strip()}
    for ext in ("tsv", "bigrams"):
        path = f"{assets}/{lang}.{ext}"
        if not os.path.exists(path):
            continue
        with open(path, encoding="utf8") as f:
            lines = f.readlines()
        # tsv is "word<sep>count", bigrams is "w1 w2<TAB>count": every field but the count is a word
        keep = [l for l in lines if not blocked & {w.lower() for w in l.split()[:-1]}]
        with open(path, "w", encoding="utf8") as f:
            f.writelines(keep)
        print(path, "removed", len(lines) - len(keep), "left", len(keep))
