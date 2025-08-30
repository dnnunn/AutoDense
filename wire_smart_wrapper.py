#!/usr/bin/env python3
import sys
from pathlib import Path
import yaml

SPEC_PATHS = {
    "sds_page":      Path("challenge_packs/sds_page_v1/spec.yaml"),
    "etbr_agarose":  Path("challenge_packs/etbr_v1/spec.yaml"),
    "colony_count":  Path("challenge_packs/colony_count_v1/spec.yaml"),
}

RUN_CMDS = {
    "sds_page":     "python scripts/with_preflight_and_rescue.py --task sds_page --input {input} --outdir {outdir} --base-config configs/sds.yaml --priors-json meta/priors_all.json",
    "etbr_agarose": "python scripts/with_preflight_and_rescue.py --task etbr_agarose --input {input} --outdir {outdir} --base-config configs/etbr.yaml --priors-json meta/priors_all.json",
    "colony_count": "python scripts/with_preflight_and_rescue.py --task colony_count --input {input} --outdir {outdir} --base-config configs/colony.yaml --priors-json meta/priors_all.json",
}

def rewire(path: Path, run_cmd: str):
    if not path.exists():
        print(f"[skip] {path} not found")
        return False
    data = yaml.safe_load(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        print(f"[err ] {path} does not look like YAML dict")
        return False
    data.setdefault("runner", {})
    before = data["runner"].get("run_cmd")
    data["runner"]["run_cmd"] = run_cmd
    bak = path.with_suffix(path.suffix + ".bak")
    bak.write_text(yaml.safe_dump(yaml.safe_load(path.read_text(encoding="utf-8")), sort_keys=False), encoding="utf-8")
    path.write_text(yaml.safe_dump(data, sort_keys=False), encoding="utf-8")
    print(f"[ok  ] {path} runner.run_cmd updated")
    if before and before != run_cmd:
        print(f"       was: {before}")
    print(f"       now: {run_cmd}")
    return True

def main():
    # Allow running from repo root or any subdir; resolve relative paths
    root = Path.cwd()
    changed = 0
    for task, spec in SPEC_PATHS.items():
        # try both from CWD and if not exists, search upward for repo root
        path = (root / spec)
        if not path.exists():
            # search up to 4 parents for the spec
            found = None
            cur = root
            for _ in range(4):
                test = cur / spec
                if test.exists():
                    found = test; break
                cur = cur.parent
            if found:
                path = found
            else:
                print(f"[skip] {spec} not found under {root}")
                continue
        if rewire(path, RUN_CMDS[task]):
            changed += 1
    print(f"Changed {changed} spec(s).")
    if changed == 0:
        sys.exit(1)

if __name__ == "__main__":
    main()
