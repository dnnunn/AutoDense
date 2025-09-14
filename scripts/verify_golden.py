import csv, math, argparse, pathlib
from PIL import Image, ImageChops, ImageStat

def read_csv(path):
    with open(path, newline='') as f:
        return list(csv.DictReader(f))

def assert_close(a,b,tol):
    if abs(a-b) > tol:
        raise AssertionError(f"{a} !≈ {b} (tol={tol})")

def compare_tables(cur, ref, key):
    assert len(cur)==len(ref), f"row count {len(cur)} != {len(ref)}"
    cur = sorted(cur, key=lambda r: r[key]); ref = sorted(ref, key=lambda r: r[key])
    for c,r in zip(cur,ref):
        assert c[key]==r[key], f"key mismatch {c[key]} vs {r[key]}"
        for k in r:
            if k==key: continue
            try:
                assert_close(float(c[k]), float(r[k]), 0.05)  # 5% tolerance
            except ValueError:
                if c[k] != r[k]:
                    raise AssertionError(f"val mismatch {k}: {c[k]} != {r[k]}")

def compare_images(cur, ref, psnr_min=35.0):
    a, b = Image.open(cur).convert('RGB'), Image.open(ref).convert('RGB')
    if a.size != b.size:
        raise AssertionError(f"size {a.size}!={b.size}")
    diff = ImageChops.difference(a,b)
    mse = sum(v*v for v in ImageStat.Stat(diff).mean)/3.0
    psnr = 10 * math.log10((255.0**2)/(mse+1e-9))
    if psnr < psnr_min:
        raise AssertionError(f"PSNR {psnr:.2f} < {psnr_min}")

if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--artifacts", required=True)
    ap.add_argument("--gold", required=True)
    args = ap.parse_args()
    A, G = pathlib.Path(args.artifacts), pathlib.Path(args.gold)
    compare_tables(read_csv(A/'colonies.csv'), read_csv(G/'colonies.csv'), key='id')
    compare_images(A/'overlay.png', G/'overlay.png')
    compare_tables(read_csv(A/'bands.csv'), read_csv(G/'bands.csv'), key='id')
    compare_images(A/'bands_overlay.png', G/'bands_overlay.png')
    # Extended MW verification if present  
    if (A/'mw_table.csv').exists() and (G/'mw_table.csv').exists():
        compare_tables(read_csv(A/'mw_table.csv'), read_csv(G/'mw_table.csv'), key='id')