You’ve basically got three separate bugs stepping on each other:

1. **The preset wires the parameters to the wrong tool.**
    Your plan shows `imagej.open_image` receiving `{"lane_count": 12}` (nonsense), and then `imagej.sds.quantify_lanes` runs with default params. In the artifacts you can see:
   - `expected_lanes: 1`
   - `lanes_found: 1`
      So the 12 you asked for never reached lane finding.
2. **The preset doesn’t call band detection at all.**
    The executed tools were: `open_image → sds.quantify_lanes → overlay.annotate → export.results`. No `detect_bands`, so you’ll never see bands.
3. **`open_image` is failing but the plan continues anyway.**
    You log `IllegalArgumentException: Missing path` for `imagej.open_image`, yet later steps run against a previously-open image handle. That’s why the ROI Manager pops up and why “the image after the first lane find never changes”—you’re not re-opening or switching the working image/overlay correctly.

There’s also a harmless startup warning:

- `Cannot create plugin: ... JavaScriptScriptLanguage` — that’s just ImageJ’s optional JS engine; unrelated to gels.

------

## What to fix (surgical and minimal)

### A) Fix parameter plumbing in the preset

Your preset `SDS_PAGE_DENSITOMETRY` should **not** pass `lane_count` to `open_image`. It should pass it to `detect_lanes` (or whatever wrapper you’ve named `sds.quantify_lanes`). Also accept `marker_lane` from the prompt.

**Before (what your logs show conceptually):**

```
[
  {"tool_id": "imagej.open_image", "inputs": {"lane_count": 12}},
  {"tool_id": "imagej.sds.quantify_lanes", "inputs": {}},
  {"tool_id": "imagej.overlay.annotate", "inputs": {}},
  {"tool_id": "export.results", "inputs": {}}
]
```

**After (intended):**

```
[
  {"tool_id": "imagej.open_image", "inputs": {"path": "<current_or_dropped_image_path>"}},
  {"tool_id": "preprocess", "inputs": {"mode": "coomassie_default"}},
  {"tool_id": "detect_lanes", "inputs": {"expected_lanes": 12, "marker_lane": 1, "constant_spacing": false}},
  {"tool_id": "detect_bands", "inputs": {"method": "peak", "min_prominence": 0.06, "smooth_sigma": 2.0, "min_peak_distance": 18}},
  {"tool_id": "render_overlay_png", "inputs": {"lane_color": "blue", "band_color": "lime", "thickness": 2}},
  {"tool_id": "export_results", "inputs": {}}
]
```

Key points:

- `expected_lanes` must be 12, not the default 1.
- Don’t call `sds.quantify_lanes` if it both finds lanes *and* quantifies bands; if it only finds lanes, follow with `detect_bands`.
- Add `preprocess` (see below) so the signal is consistent.

### B) Don’t run `open_image` when an image is already current

When the user drags a file into your UI, you already have a current `image_handle`. Either:

- Skip `open_image` entirely if `currentImage != null`, **or**
- Call `open_image` with that absolute path. Never call it with *params meant for other tools*.

Guard in orchestrator:

```
if (context.hasImageHandle()) {
  plan.removeFirstIf(toolIdEquals("imagej.open_image"));
}
```

…and in the tool:

```
String path = inputs.getString("path");
if (path == null) throw new IllegalArgumentException("Missing path");
```

### C) Make lane finding robust for this gel

From your images (`/mnt/data/IMG_9715-2.jpg`), you have 12 lanes, first is a ladder; lanes are slightly slanted and spacing is not perfectly constant.

Recommended defaults for Coomassie-like gels:

- Convert to grayscale, invert only if your algorithms expect dark-on-light.
- Background: **lane-wise rolling-ball** (radius ~50–70 px) or a morphological opening per lane.
- Lane search:
  - `constant_spacing: false`
  - `lane_width_fraction: 0.35–0.45` (your log had 0.55, too fat → merges lanes)
  - `min_peak_distance: 18–22 px` in the horizontal lane-profile
  - Use vertical smoothing (e.g., Gaussian σ=2) before peak-picking.

### D) Actually detect bands (peak finding on each lane’s vertical profile)

- Smooth each lane intensity trace (σ≈1.5–2.0).
- Set `min_prominence` ~ **6–8%** of lane max for stained gels like yours.
- Enforce `min_peak_distance` 8–12 px vertically to avoid double-picking thick bands.
- Optional: adaptive prominence using percentile of local noise.

### E) Keep ROI Manager out of the party

ImageJ’s `Gel Analyzer` can spawn ROI Manager. You’re managing overlays yourself, so hide/suppress it:

```
RoiManager rm = RoiManager.getInstance2();
if (rm != null) {
  rm.setVisible(false);
  rm.reset();
}
Overlay ov = img.getOverlay();
if (ov == null) { ov = new Overlay(); img.setOverlay(ov); }
// draw lanes/bands onto ov only; never add ROIs to RM
```

And if any of your tools call `run("Gel Analyzer...")`, swap that for programmatic profiles instead of the macro command.

### F) Ensure overlay refreshes the displayed image

After `render_overlay_png` (or your overlay tool), explicitly update the window and bring it to front:

```
img.updateAndDraw();
WindowManager.toFront(img.getWindow());
```

If you’re writing a PNG to disk and previewing that, make sure you replace the *displayed canvas* or layer rather than writing a temp file that never gets used.

------

## Suggested presets (drop-in)

**Preprocess (Coomassie default)**

- `convertToGray`
- Optional `invert=false` (only invert if your analysis expects dark peaks)
- `enhanceContrast(0.35, normalize=true, saturated=0.3%)`
- `rollingBall(radius=60)` per-lane (or 2D rolling-ball if per-lane is not yet implemented)
- `gaussianBlur(sigma=1.0)` lightly

**Detect lanes**

- Horizontal projection profile across the central vertical band mass
- Peak-pick with `expected_lanes=12`, non-constant spacing, width estimation via half-prominence width
- Snap lane edges to local minima to prevent leakage between lanes

**Detect bands**

- For each lane, vertical profile → smooth → peaks with adaptive prominence
- Link bands horizontally across lanes to form “rows” (useful for isoform profiling)

**Render overlay**

- Lanes: semi-transparent blue rectangles
- Bands: lime horizontal strokes; label ladder bands if you calibrate later

------

## Tiny code patches you’ll likely need

**1) Preset parameter pass-through**

```
// In workflows.preset(SDS_PAGE_DENSITOMETRY)
JSONObject params = inputs.optJSONObject("params");
int expected = params != null ? params.optInt("lane_count", 12) : 12;
int marker = params != null ? params.optInt("marker_lane", 1) : 1;

steps.add(step("preprocess", json("mode", "coomassie_default")));
steps.add(step("detect_lanes", json(
  "expected_lanes", expected,
  "marker_lane", marker,
  "constant_spacing", false
)));
steps.add(step("detect_bands", json(
  "method", "peak",
  "min_prominence", 0.06,
  "smooth_sigma", 2.0,
  "min_peak_distance", 18
)));
steps.add(step("render_overlay_png", json(
  "lane_color", "blue",
  "band_color", "lime",
  "thickness", 2
)));
steps.add(step("export_results", json()));
```

**2) Short-circuit `open_image` if an image is already selected**

```
if (context.hasImageHandle()) {
  log.debug("Skipping open_image; using current handle {}", context.imageHandle());
} else {
  tools.run("open_image", json("path", context.pendingPath()));
}
```

**3) Hide ROI Manager everywhere**
 Add a helper `ensureOverlayOnly(imgPlus)` and call it at the start of every gel tool.

------

## Sanity checks to add to your DEBUGGING_CHECKLIST

- After planning, **echo the inputs** for each step before execution and assert on required fields (e.g., `expected_lanes >= 1`).
- If any tool throws (like `open_image` here), **abort the plan** unless you have a clear fallback image handle.
- After `detect_lanes`, log `{expected_lanes, lanes_found, lane_width_px, mean_spacing_px}`.
- After `detect_bands`, log `bands_per_lane` histogram.

------

## Suggested parameters for *this* gel (from your photos)

- `expected_lanes: 12`
- `marker_lane: 1`
- `lane_width_fraction: 0.4`
- `constant_spacing: false`
- `min_peak_distance (horizontal lanes): 20`
- `min_peak_distance (vertical bands): 10`
- `min_prominence: 0.06–0.08`
- Background: rolling-ball radius 60 px

If you want, I can draft the exact `SDS_PAGE_DENSITOMETRY` preset file and a small `LaneWiseBackground` helper that computes a per-lane baseline using a running quantile—handy for your uneven stain across the gel.

##### You said:

yes, thanks

###### ChatGPT said:

Thought for 7s

Awesome — here’s the drop-in preset and the tiny helpers we discussed so Gemini stops “finding” exactly one lane forever.

# 1) New preset: `SdsPageDensitometryPreset.java`

Put this next to your other workflow presets (same package as `workflows.preset` runner uses). It wires parameters to the **right** tools and inserts preprocessing + band detection.

```
package autodense.workflows;

import org.json.JSONObject;
import autodense.core.Plan;
import autodense.core.Step;
import autodense.core.Context;

public final class SdsPageDensitometryPreset {

  // Entry point used by workflows.preset tool
  public static Plan build(Context ctx, JSONObject params) {
    final int expectedLanes = params != null ? params.optInt("lane_count", 12) : 12;
    final int markerLane    = params != null ? params.optInt("marker_lane", 1) : 1;

    Plan plan = new Plan("SDS_PAGE_DENSITOMETRY");

    // Only open if no current image handle (see §3 guard too)
    if (!ctx.hasImageHandle()) {
      // You likely stash the path from drag-drop into ctx.pendingPath()
      plan.add(Step.of("open_image", new JSONObject().put("path", ctx.pendingPath())));
    }

    plan
      .add(Step.of("preprocess", new JSONObject()
        .put("mode", "coomassie_default"))) // see §2 implementation

      .add(Step.of("detect_lanes", new JSONObject()
        .put("expected_lanes", expectedLanes)
        .put("marker_lane", markerLane)
        .put("constant_spacing", false)
        .put("lane_width_fraction", 0.40)   // thinner than your previous 0.55
        .put("min_peak_distance", 20)))     // horizontal lane search

      .add(Step.of("detect_bands", new JSONObject()
        .put("method", "peak")
        .put("smooth_sigma", 2.0)
        .put("min_prominence", 0.06)
        .put("min_peak_distance", 10)))    // vertical band spacing

      .add(Step.of("render_overlay_png", new JSONObject()
        .put("lane_color", "blue")
        .put("band_color", "lime")
        .put("thickness", 2)))

      .add(Step.of("export_results", new JSONObject()));

    return plan;
  }
}
```

# 2) Preprocess mode: `coomassie_default`

Add this to your `preprocess` tool (switch on `"mode"`). It keeps ROI Manager hidden and creates a sane, lane-wise background.

```
// inside your preprocess tool
private void coomassieDefault(ImagePlus imp) {
  IJUtils.silenceRoiManager(imp);                 // §4
  IJ.run(imp, "8-bit", "");
  // Do NOT invert for Coomassie if downstream expects dark-on-light=false
  IJ.run(imp, "Enhance Contrast...", "saturated=0.3 normalize");
  // Gentle denoise before baseline
  IJ.run(imp, "Gaussian Blur...", "sigma=1");
  // Lane-wise background (below)
  LaneWiseBackground.subtract(imp, /*radiusPx=*/60, /*quantile=*/0.15);
  imp.updateAndDraw();
}
```

# 3) Orchestrator guard: skip `open_image` when not needed

This prevents “Missing path” while the plan happily continues on a stale handle.

```
// right before executing a plan
if (ctx.hasImageHandle()) {
  plan.removeFirst(step -> "open_image".equals(step.toolId()));
} else {
  // enforce required input if we do open
  plan.assertFirstHas("open_image", "path");
}
```

# 4) ROI Manager suppression + overlay hygiene

Drop this small utility and call it at the start of every gel tool.

```
package autodense.img;

import ij.ImagePlus;
import ij.gui.Overlay;
import ij.plugin.frame.RoiManager;

public final class IJUtils {
  public static void silenceRoiManager(ImagePlus imp) {
    RoiManager rm = RoiManager.getInstance2();
    if (rm != null) {
      rm.reset();
      rm.setVisible(false);
    }
    Overlay ov = imp.getOverlay();
    if (ov == null) {
      imp.setOverlay(new Overlay());
    }
  }

  public static void refresh(ImagePlus imp) {
    imp.updateAndDraw();
    if (imp.getWindow() != null) imp.getWindow().toFront();
  }
}
```

Call `IJUtils.refresh(imp);` at the end of `detect_lanes`, `detect_bands`, and `render_overlay_png` so the display actually updates.

# 5) Lane-wise background helper

This avoids over-wide lanes merging and compensates uneven stain. Simple running-quantile per lane; fast and robust for your gel (`/mnt/data/IMG_9715-2.jpg`).

```
package autodense.img;

import ij.ImagePlus;
import ij.process.ImageProcessor;

public final class LaneWiseBackground {

  // radiusPx ~ 50–70 works well on 4k smartphone gel photos
  public static void subtract(ImagePlus imp, int radiusPx, double quantile) {
    ImageProcessor ip = imp.getProcessor();
    final int w = ip.getWidth(), h = ip.getHeight();

    // crude lane width guess ~ 0.40 of inter-lane spacing; fall back to 0.4 * (w/expectedLanes)
    final int window = Math.max(3, radiusPx);
    float[] colBuf = new float[h];
    float[] bgCol  = new float[h];

    for (int x = 0; x < w; x++) {
      // vertical column snapshot
      for (int y = 0; y < h; y++) colBuf[y] = ip.getf(x, y);

      // running quantile baseline per column (O(h*window))
      runningQuantile(colBuf, bgCol, window, quantile);

      for (int y = 0; y < h; y++) {
        float val = colBuf[y] - bgCol[y];
        ip.setf(x, y, Math.max(0f, val));
      }
    }
  }

  // very small, simple quantile via sliding window insertion sort (good enough here)
  private static void runningQuantile(float[] src, float[] out, int win, double q) {
    final int n = src.length;
    final int half = win/2;
    float[] buf = new float[win];
    for (int i = 0; i < n; i++) {
      int a = Math.max(0, i - half), b = Math.min(n - 1, i + half);
      int len = b - a + 1;
      if (len != buf.length) buf = new float[len];
      for (int k = 0; k < len; k++) buf[k] = src[a + k];
      java.util.Arrays.sort(buf, 0, len);
      int idx = (int)Math.floor(q * (len - 1));
      out[i] = buf[idx];
    }
  }
}
```

# 6) Safer defaults inside your lane/band tools

If you expose these as tool parameters, set these as in-code fallbacks when missing:

```
// detect_lanes defaults
expected_lanes = inputs.optInt("expected_lanes", 12);
constant_spacing = inputs.optBoolean("constant_spacing", false);
lane_width_fraction = (float) inputs.optDouble("lane_width_fraction", 0.40);
min_peak_distance = inputs.optInt("min_peak_distance", 20);

// detect_bands defaults
smooth_sigma = inputs.optDouble("smooth_sigma", 2.0);
min_prominence = inputs.optDouble("min_prominence", 0.06);
min_peak_distance = inputs.optInt("min_peak_distance", 10);
```

# 7) Debug statements that actually help

Right after each stage, log these:

```
[LANES] expected=12 found=<n> lane_width_px=<mean±sd> spacing_px=<mean±sd>
[BANDS] lane i=<k>, peaks=<m>, prominence>=<p>, sigma=<s>
```

That will make failures obvious in one glance.