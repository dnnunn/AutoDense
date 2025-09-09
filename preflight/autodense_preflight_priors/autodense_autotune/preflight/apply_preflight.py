from __future__ import annotations
import json, argparse
from pathlib import Path
import yaml
from . import preflight_etbr, preflight_sds, preflight_colony
from .common import save_yaml

def load_json_if_exists(p):
    if not p: return None
    path = Path(p)
    return json.loads(path.read_text(encoding="utf-8")) if path.exists() else None

def load_yaml_if_exists(p):
    if not p: return {}
    path = Path(p)
    return yaml.safe_load(path.read_text(encoding="utf-8")) if path.exists() else {}

def deep_merge(a: dict, b: dict) -> dict:
    out = dict(a)
    for k,v in b.items():
        if isinstance(v, dict) and isinstance(out.get(k), dict):
            out[k] = deep_merge(out[k], v)
        else:
            out[k] = v
    return out

def main():
    ap = argparse.ArgumentParser(description="Compute per-image preflight defaults and write an adjusted config YAML.")
    ap.add_argument("--task", required=True, choices=["etbr_agarose","sds_page","colony_count"])
    ap.add_argument("--input", required=True)
    ap.add_argument("--base-config", default=None)
    ap.add_argument("--priors-json", default=None)
    ap.add_argument("--out-config", required=True)
    args = ap.parse_args()

    priors = load_json_if_exists(args.priors_json)
    if args.task == "etbr_agarose":
        defaults = preflight_etbr(args.input, priors)
    elif args.task == "sds_page":
        defaults = preflight_sds(args.input, priors)
    else:
        defaults = preflight_colony(args.input, priors)

    base = load_yaml_if_exists(args.base_config)
    merged = deep_merge(base, defaults)
    save_yaml(merged, args.out_config)
    print(args.out_config)

if __name__ == "__main__":
    main()
