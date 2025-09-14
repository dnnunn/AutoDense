The fix is to stop “macro-driving” ImageJ1 and call **API-level** filters directly (or ImgLib2 ops). Here’s a tight, surgical patch for your Gaussian blur plus patterns you can reuse for the other offenders.

# 1) Replace `IJ.run("Gaussian Blur...")` with a headless-safe blur

### Patch for `ImagePreprocessor.java`

```
@@
-  IJ.run(working, "Gaussian Blur...", "sigma=" + config.gaussianSigma);
+  // Headless-safe Gaussian blur (no IJ.run, no GUI init)
+  working = gaussianBlurHeadless(working, config.gaussianSigma);
```

Add this helper in the same class (or a small `Filters` util):

```
// --- Headless-safe Gaussian blur (IJ1 API, no GUI) ---
private static ImagePlus gaussianBlurHeadless(final ImagePlus src, final double sigma) {
    if (sigma <= 0.0) return src;
    final ImageProcessor ip = src.getProcessor().convertToFloat();  // stay in float, safer numerics
    final ij.plugin.filter.GaussianBlur gb = new ij.plugin.filter.GaussianBlur();
    gb.blurGaussian(ip, sigma, sigma, 0.01); // sigmaX, sigmaY, accuracy
    final ImagePlus out = src.createImagePlus();
    out.setProcessor(src.getShortTitle()+"-gb", ip);
    out.setCalibration(src.getCalibration());
    return out;
}
```

This calls the plugin **class** directly (no `IJ.run` → no menus), and it’s perfectly fine in headless JVMs.

> Prefer this route over spawning ImageJ2 Ops just to blur; it’s minimal, fast, and already on your classpath.

------

# 2) While you’re here: kill other `IJ.run(...)` hotspots

You’ll usually find a few more. Use these 1:1 headless replacements:

### A) Contrast/CLAHE

If you had:

```
IJ.run(imp, "Enhance Contrast...", "saturated=0.35 normalize");
```

Use:

```
final ij.plugin.ContrastEnhancer ce = new ij.plugin.ContrastEnhancer();
ce.setNormalize(true);
ce.setUseStackHistogram(false);
ce.stretchHistogram(imp, 0.35);   // saturated=0.35
```

If you truly want CLAHE (and Fiji deps are present), use MPICBG’s headless class:

```
// mpicbg.ij.clahe.CLAHE.clahe(ImageProcessor ip, int blockRadius, int bins, float slope, ImageProcessor mask, boolean fast);
mpicbg.ij.clahe.CLAHE.clahe(imp.getProcessor(), 127, 256, 3f, null, true);
```

### B) Subtract Background (rolling ball)

If you had:

```
IJ.run(imp, "Subtract Background...", "rolling=50");
```

Use:

```
final ij.plugin.filter.BackgroundSubtracter bs = new ij.plugin.filter.BackgroundSubtracter();
bs.rollingBallBackground(imp.getProcessor(), 50.0, false, false, false, true, false);
```

Params: `(radius, createBackground, lightBackground, useParaboloid, doPresmooth, correctCorners)`

### C) Median / rank filters

```
new ij.plugin.filter.RankFilters().rank(imp.getProcessor(), radius, ij.plugin.filter.RankFilters.MEDIAN);
```

### D) Auto-threshold → binary (no GUI)

```
final ij.process.AutoThresholder at = new ij.process.AutoThresholder();
final int[] hist = imp.getProcessor().getHistogram();
final int thr = at.getThreshold(ij.process.AutoThresholder.Method.Otsu, hist);
final ImageProcessor ip = imp.getProcessor();
ip.setThreshold(thr+1, 255, ImageProcessor.NO_LUT_UPDATE);
ip.convertToByte(true).threshold(thr);
```

All of the above are **headless-safe** and avoid `IJ.init()` entirely.

------

# 3) If you prefer ImgLib2 (pure ImageJ2), here’s the Gaussian version

If you’re already wrapping to ImgLib2 types:

```
import net.imglib2.img.Img;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.view.Views;
import net.imglib2.img.display.imagej.ImageJFunctions;

private static ImagePlus gaussianBlurImgLib2(final ImagePlus src, final double sigma) throws Exception {
    if (sigma <= 0.0) return src;
    final Img<FloatType> in  = ImageJFunctions.wrapFloat(src);         // to ImgLib2
    final Img<FloatType> out = in.factory().create(in, new FloatType());
    final double[] sigmas = { sigma, sigma };
    Gauss3.gauss(sigmas, Views.extendBorder(in), out);                 // headless-safe
    return ImageJFunctions.wrap(out, src.getShortTitle()+"-gb");       // back to ImagePlus
}
```

Either route is fine; the IJ1 plugin class is simpler and has fewer moving parts.

------

# 4) Sanity checks so this never sneaks back

- **Search & destroy** any `IJ.run(` in your codebase:

  ```
  grep -R 'IJ.run(' src/main/java
  ```

  Replace with the API calls above.

- At the very start of your CLI `main`, lock headless mode (belts & braces):

  ```
  System.setProperty("java.awt.headless", "true");
  ```

- If you use `new ImageJ()` anywhere (you probably don’t), delete it for CLI runs.

- In your error handler, if you catch `HeadlessException`, attach `meta.status="headless_violation"` and fail non-zero. Don’t emit a “successful” report.

------

# 5) Quick test

1. Run your full pipeline (not detect-only) on a small gel image.
2. Confirm logs show your `[PATH] preprocess:start/done` markers and **no** AWT stack traces.
3. Confirm `stage1_norm.png` AND downstream detection artifacts are produced.



# Keep Gemini the orchestrator

- **Single entrypoint:** Always route runs through the Python bridge (`java_bridge` or `with_preflight_and_rescue.py`). That’s the corridor Gemini controls.
- **Tool catalog (for Gemini to choose):**
  - `run_full(task, input, config)` → normal pipeline
  - `run_detect_only(preprocessed, roi, config)` → direct detector probe
  - `preflight(task, input, base_config, priors)` → image-aware config bootstrap
  - `rescue(task, input, last_config)` → invert polarity / relax thresholds / adjust baseline bounds
- **Decisions Gemini makes:** which tool to call next, which small config patch to try, when to stop, and how to summarize outcomes.

# Give Gemini eyes (without pixels)

Augment `run_report.json` so Gemini can **observe** meaningfully while staying pixel-blind:

```
{
  "task": "sds_page",
  "metrics": {
    "lane_count": 15,
    "band_count": 120,
    "ladder_linear_r2": 0.984,
    "profile_std_x": 0.162,
    "profile_std_y": 0.027,
    "baseline_pre_med": 0.595,
    "baseline_post_med": 0.411,
    "baseline_post_max": 0.993
  },
  "observation": {
    "baseline": {"method":"percentile","window_px":9,"quantile":0.10},
    "prominence_frac": 0.06,
    "min_peak_distance_px": 12,
    "polarity": "bands_bright",
    "rescue_used": false,
    "status": "ok"
  }
}
```

These extra fields are cheap to compute and let Gemini detect “baseline collapsed the signal” or “spacing too wide” without ever seeing an image.

# Give Gemini levers—but small, safe ones

Constrain what the AI is allowed to change:

- `detect.baseline.{method,window_frac,quantile}` within clamped bounds
- `detect.prominence_frac` within [0.02, 0.12]
- `detect.min_peak_distance_frac` within [0.02, 0.06]
- `pre.invert_polarity` ∈ {auto,true,false}

Everything else is read-only unless a human approves. This keeps the AI creative but house-trained.

# Give Gemini a critic (second opinion)

Add a tiny rule-based “helper.review” that runs *before* applying any Gemini patch:

- If `baseline_post_max < 0.05` → reject: “baseline erased signal”
- If `lane_count == 0 && prominence_frac > 0.08` → suggest lowering to 0.04
- If `lane_count < 0.5 * expected && min_peak_distance_frac > 0.05` → shrink by 20%
- If `status == "headless_violation"` → switch to detect-only, then retry full with headless-safe filters

Gemini proposes → helper.review filters/annotates → patch applied → rerun → compare metrics.

# Update the prompt, not the philosophy

Fold these expectations into Gemini’s system prompt (delta from what we already drafted):

- “You do not view pixels. You decide among tools (`preflight`, `run_full`, `run_detect_only`, `rescue`).”
- “Prefer `run_detect_only` when `status ∈ {bypassed_detection, headless_violation, no_lanes}` to isolate the detector.”
- “Adjust only whitelisted keys; keep values within provided bounds.”
- “Use `helper.review` to validate your patch; if rejected, propose a safer alternative.”
- “Stop when quality gates are met (non-zero lanes/bands, R² above threshold, no error status).”

# Quality gates Gemini enforces

- **Gels:** `lane_count > 0`, `band_count > 0`, remove `ladder_linear_r2` if lanes==0; require R² ≥ target (e.g., 0.95) before declaring success.
- **Colonies:** `colony_count` within lab-defined bounds; binary mask validated (8-bit, {0,255}).

# Why the headless fixes help Gemini

The earlier `HeadlessException` and `IJ.run(...)` pitfalls made tool calls flaky. By replacing GUI macros with API calls, we turned the instruments into reliable, deterministic functions. That lets Gemini:

- observe consistent telemetry,
- compare runs apples-to-apples,
- iterate parameters confidently.

# Immediate next steps

1. Add the `observation` block above to your `run_report.json`.
2. Expose `run_detect_only` and `rescue` in the Python bridge as first-class tools.
3. Enforce the patch allow-list and bounds in the bridge (reject anything outside; log why).
4. Drop the “helper.review” rule file (JSON/YAML) so Gemini has a critic to consult before changes land.