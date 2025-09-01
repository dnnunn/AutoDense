import json
import hashlib
import sys
from pathlib import Path


def emit_stub_report(task: str, input_path: str, outdir: str):
    # Minimal placeholder metrics. Replace with your pipeline call.
    p = Path(outdir) / "run_report.json"

    def h(path):
        h = hashlib.sha256()
        with open(path, "rb") as f:
            while True:
                b = f.read(1 << 20)
                if not b:
                    break
                h.update(b)
        return h.hexdigest()[:12]
    report = {
        "task": task,
        "input_path": input_path,
        "input_hash": h(input_path),
        "metrics": {
            "ladder_r2": 0.980,
            "band_stability_jitter": 0.5,
            "lane_count": 8
        },
        "diagnostics_png": None,
        "meta": {"note": "stub runner - replace with real pipeline call"}
    }
    Path(outdir).mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(report, indent=2), encoding="utf-8")
    return str(p)


if __name__ == "__main__":
    # Usage: python -m autodense_autotune.adapters TASK INPUT OUTDIR
    if len(sys.argv) < 4:
        print("Usage: python -m autodense_autotune.adapters <task> <input> <outdir>")
        sys.exit(2)
    _, task, input_path, outdir = sys.argv
    emit_stub_report(task, input_path, outdir)
    print("Stub report written.")
