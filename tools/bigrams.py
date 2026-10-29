"""Word-pair counts from Tatoeba sentences -> assets/<lang>.bigrams ("w1 w2<TAB>count"), top 8 next words per word.
Usage: python tools/bigrams.py dict-src/eng_sentences.tsv.bz2 app/src/main/assets/en.tsv app/src/main/assets/en.bigrams"""
import bz2, collections, re, sys

src, vocab_path, out = sys.argv[1:4]
vocab = {l.split()[0].lower() for l in open(vocab_path, encoding="utf8") if l.strip()}
word = re.compile(r"[^\W\d_]+(?:'[^\W\d_]+)?")
pairs = collections.Counter()
with bz2.open(src, "rt", encoding="utf8") as f:
    for line in f:
        parts = line.rstrip("\n").split("\t")
        if len(parts) < 3:
            continue
        # sentence boundaries and punctuation break pairs: only words written next to each other count
        for chunk in re.split(r"[.,;:!?؟،؛()\"“”«»\-–—]+", parts[2]):
            ws = [w.lower() for w in word.findall(chunk)]
            for a, b in zip(ws, ws[1:]):
                if a in vocab and b in vocab:
                    pairs[(a, b)] += 1
best = collections.defaultdict(list)
for (a, b), c in pairs.most_common():
    if c >= 2 and len(best[a]) < 8:
        best[a].append((b, c))
with open(out, "w", encoding="utf8") as o:
    for a in sorted(best):
        for b, c in best[a]:
            o.write(f"{a} {b}\t{c}\n")
print(out, sum(map(len, best.values())), "pairs for", len(best), "words")
