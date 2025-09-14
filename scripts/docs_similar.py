#!/usr/bin/env python3
import sys, re
from pathlib import Path
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
docs, paths = [], []
for p in sorted(ROOT.rglob("*.md")):
    if ".git" in p.parts: continue
    t = p.read_text(encoding="utf-8", errors="ignore")
    t = re.sub(r"```.*?```", "", t, flags=re.S)  # strip code blocks
    t = re.sub(r"\W+", " ", t.lower())
    docs.append(t); paths.append(p)

vec = TfidfVectorizer(ngram_range=(1,2), min_df=2).fit_transform(docs)
sim = cosine_similarity(vec, vec)

THRESH = 0.80
pairs = []
n = len(paths)
for i in range(n):
    for j in range(i+1, n):
        if sim[i,j] >= THRESH:
            pairs.append((sim[i,j], paths[i], paths[j]))

pairs.sort(reverse=True)
for s, a, b in pairs[:100]:
    print(f"{s:.3f}\t{a.relative_to(ROOT)}\t{b.relative_to(ROOT)}")