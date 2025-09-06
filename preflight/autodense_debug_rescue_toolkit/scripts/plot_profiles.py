#!/usr/bin/env python
import numpy as np
from PIL import Image
from pathlib import Path
import argparse, matplotlib.pyplot as plt

def load_gray(path: str) -> np.ndarray:
    img = Image.open(path)
    arr = np.asarray(img.convert("RGB"), dtype=np.float32) / 255.0
    r,g,b = arr[...,0], arr[...,1], arr[...,2]
    return 0.2126*r + 0.7152*g + 0.0722*b

def normalize_p1p99(img: np.ndarray):
    p1,p99 = np.percentile(img, [1,99])
    if p99 > p1:
        img = np.clip((img - p1) / (p99 - p1), 0, 1)
    return img

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", required=True)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()
    img = load_gray(args.input)
    img = normalize_p1p99(img)
    # vertical projection
    prof = img.mean(axis=0)
    fig = plt.figure()
    plt.plot(prof)  # no explicit colors/styles
    plt.title("Horizontal projection (for lane detection)")
    plt.xlabel("x"); plt.ylabel("mean intensity")
    fig.savefig(args.out, dpi=150, bbox_inches="tight")
    print(args.out)

if __name__ == "__main__":
    main()
