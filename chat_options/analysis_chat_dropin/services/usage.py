
from __future__ import annotations
import os, json, time, pathlib, threading
from typing import Dict, Any, List

LOG_PATH = os.getenv("AUTODENSE_TOOL_USAGE_PATH", ".autodense/usage_log.jsonl")
_lock = threading.Lock()

def _ensure_parent(p: pathlib.Path):
    p.parent.mkdir(parents=True, exist_ok=True)

def record_call(tool: str, args: Dict[str, Any] | None = None):
    rec = {"ts": int(time.time()), "tool": tool, "args": args or {}}
    p = pathlib.Path(LOG_PATH).expanduser().resolve()
    _ensure_parent(p)
    line = json.dumps(rec, ensure_ascii=False)
    with _lock:
        with open(p, "a", encoding="utf-8") as f:
            f.write(line + "\n")

def read_recent(n: int = 5000) -> List[Dict[str, Any]]:
    p = pathlib.Path(LOG_PATH).expanduser()
    if not p.exists():
        return []
    # Read tail-ish efficiently
    with open(p, "r", encoding="utf-8") as f:
        lines = f.readlines()[-n:]
    out = []
    for ln in lines:
        try:
            out.append(json.loads(ln))
        except Exception:
            continue
    return out
