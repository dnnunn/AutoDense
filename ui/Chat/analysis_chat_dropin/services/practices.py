
from __future__ import annotations
from typing import Dict, Any, List, Optional
import json, os, pathlib, time

DEFAULT_PATH = os.getenv("AUTODENSE_PRACTICES_PATH", ".autodense/practices.json")

def _ensure_dir(path: str):
    p = pathlib.Path(path).expanduser().resolve()
    p.parent.mkdir(parents=True, exist_ok=True)
    return p

def _load(path: str = DEFAULT_PATH) -> Dict[str, Any]:
    p = pathlib.Path(path).expanduser()
    if not p.exists():
        return {"practices": {}, "meta": {"version": 1}}
    try:
        return json.loads(p.read_text(encoding="utf-8"))
    except Exception:
        return {"practices": {}, "meta": {"version": 1}}

def _save(data: Dict[str, Any], path: str = DEFAULT_PATH) -> None:
    p = _ensure_dir(path)
    p.write_text(json.dumps(data, indent=2), encoding="utf-8")

def list_practices(path: str = DEFAULT_PATH) -> List[Dict[str, Any]]:
    data = _load(path)
    out = []
    for name, rec in data.get("practices", {}).items():
        out.append({
            "name": name,
            "created_at": rec.get("created_at"),
            "updated_at": rec.get("updated_at"),
            "usage_count": rec.get("usage_count", 0),
            "desc": rec.get("desc", ""),
        })
    out.sort(key=lambda r: (-r.get("usage_count", 0), r.get("name","")))
    return out

def get_practice(name: str, path: str = DEFAULT_PATH) -> Optional[Dict[str, Any]]:
    data = _load(path)
    return data.get("practices", {}).get(name)

def save_practice(name: str, recipe: Dict[str, Any], desc: str = "", path: str = DEFAULT_PATH) -> Dict[str, Any]:
    data = _load(path)
    now = int(time.time())
    rec = data.setdefault("practices", {}).get(name, {"created_at": now, "usage_count": 0})
    rec["recipe"] = recipe
    rec["desc"] = desc
    rec["updated_at"] = now
    data["practices"][name] = rec
    _save(data, path)
    return {"status": "ok", "name": name, "desc": desc, "updated_at": now}

def delete_practice(name: str, path: str = DEFAULT_PATH) -> Dict[str, Any]:
    data = _load(path)
    if name in data.get("practices", {}):
        del data["practices"][name]
        _save(data, path)
        return {"status": "ok", "deleted": name}
    return {"status": "missing", "name": name}

def run_practice(recipe: Dict[str, Any], dispatch_fn, results: Dict[str, Any]) -> Dict[str, Any]:
    """
    'recipe' is a list of steps like:
      [{"tool":"quantify_against_standard","args":{"standard_lane":0,"fit":"linear"}},
       {"tool":"band_ratio","args":{"ref":[1,0],"target":[2,0]}}]
    """
    outputs: List[Dict[str, Any]] = []
    for step in recipe if isinstance(recipe, list) else []:
        tool = step.get("tool")
        args = step.get("args", {})
        text = json.dumps({"tool": tool, "args": args})
        out = dispatch_fn(text, results)  # reuse existing toolkit dispatcher
        outputs.append({"step": step, "output": out})
    return {"status": "ok", "outputs": outputs}

def bump_usage(name: str, path: str = DEFAULT_PATH) -> None:
    data = _load(path)
    rec = data.get("practices", {}).get(name)
    if not rec: return
    rec["usage_count"] = int(rec.get("usage_count", 0)) + 1
    rec["updated_at"] = int(time.time())
    _save(data, path)
