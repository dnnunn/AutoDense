
from __future__ import annotations
from typing import List, Dict, Any, Tuple
from collections import Counter, defaultdict
import json

def _ngram_counts(seq: List[str], min_len: int = 2, max_len: int = 4) -> Dict[Tuple[str, ...], int]:
    counts: Dict[Tuple[str, ...], int] = Counter()
    L = len(seq)
    for i in range(L):
        for k in range(min_len, max_len+1):
            if i + k <= L:
                ngram = tuple(seq[i:i+k])
                counts[ngram] += 1
    return counts

def _most_common_args(events: List[Dict[str, Any]]) -> Dict[str, Dict[str, Any]]:
    # For each tool, choose the most common args (stringified) as "typical"
    by_tool = defaultdict(list)
    for ev in events:
        tool = ev.get("tool")
        args = ev.get("args") or {}
        by_tool[tool].append(args)

    typical: Dict[str, Dict[str, Any]] = {}
    for tool, arglist in by_tool.items():
        freq = Counter(json.dumps(a, sort_keys=True) for a in arglist)
        if not freq:
            typical[tool] = {}
            continue
        best_json, _ = freq.most_common(1)[0]
        try:
            typical[tool] = json.loads(best_json)
        except Exception:
            typical[tool] = {}
    return typical

def suggest_from_usage(events: List[Dict[str, Any]], top_k: int = 5, min_support: int = 2) -> List[Dict[str, Any]]:
    tools_seq = [e.get("tool") for e in events if isinstance(e.get("tool"), str)]
    counts = _ngram_counts(tools_seq, 2, 4)
    # Filter and rank by support then length desc
    candidates = [(ng, c) for ng, c in counts.items() if c >= min_support]
    candidates.sort(key=lambda x: (x[1], len(x[0])), reverse=True)

    typical_args = _most_common_args(events)

    out: List[Dict[str, Any]] = []
    for ng, c in candidates[: top_k * 2]:  # overshoot a bit then trim
        recipe = [{"tool": t, "args": typical_args.get(t, {})} for t in ng]
        # Simple de-dup of adjacent identical tools with identical args
        deduped = []
        for step in recipe:
            if deduped and step == deduped[-1]:
                continue
            deduped.append(step)
        out.append({"support": c, "sequence": list(ng), "recipe": deduped})
        if len(out) >= top_k:
            break
    return out
