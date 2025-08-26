# How it works (deterministic, user‑assisted)

1. **Assist mode toggle** (a toolbar button): when active, a single click becomes a “seed band here” signal.
2. **Capture the click**: map screen coords → image coords → find the **lane** that contains $x$.
3. **Refine in the clicked lane**:
   - Build the **lane intensity profile** $L(y)=\sum_{x\in lane} I(x,y)$.
   - In a ±W px window around the click $y_0$, find the **nearest local maximum**; refine apex by quadratic fit; find left/right valleys to define band bounds.
   - Compute **integrated density**, **local background** (from flank strips), **SNR**, and **$R_f$** (distance/ dye‑front).
4. **Propagate to other lanes**:
   - For each other lane, search around the **same $R_f$** (or same MW if you already calibrated with a ladder) with a tolerance (e.g., $\Delta R_f = \pm 0.02$ or $\pm 5\%$ of lane height).
   - Choose the best peak by **prominence** and **SNR**; optionally compare peak width to the seed’s width.
   - Accept if it passes thresholds; record **confidence** (e.g., z‑score of prominence relative to local noise).
5. **Commit**:
   - Add a **rectangle ROI** (band box) per accepted band to the Overlay (and optionally to ROI Manager).
   - Append rows to ResultsTable (lane, y_px, distance, $R_f$, area, background, SNR, assigned MW if calibrated, confidence).
   - If nothing is found in a lane, either skip or prompt “click to confirm/add manually for lane N”.

This gives you an efficient “semi‑automatic” lane scan guided by a single human click.

------

# Implementation sketch (ImageJ2 plugin + listener)

### 1) Hook a click listener on the active canvas

```
public final class AssistBandTool {

  private final GelContext gel; // your engine with lanes already detected
  private ImageCanvas canvas;
  private MouseAdapter clicker;

  public AssistBandTool(GelContext gel) { this.gel = gel; }

  public void enable(ImagePlus imp) {
    if (canvas != null) disable();
    canvas = imp.getCanvas();
    clicker = new MouseAdapter() {
      @Override public void mouseClicked(MouseEvent e) {
        if (e.getButton() != MouseEvent.BUTTON1) return;
        int ox = canvas.offScreenX(e.getX());
        int oy = canvas.offScreenY(e.getY());
        onUserClick(imp, ox, oy);
      }
    };
    canvas.addMouseListener(clicker);
    IJ.showStatus("Assist mode: click a band to seed identification");
  }

  public void disable() {
    if (canvas != null && clicker != null) canvas.removeMouseListener(clicker);
    canvas = null; clicker = null;
  }

  private void onUserClick(ImagePlus imp, int x, int y) {
    Lane lane = gel.findLaneAtX(x); // determine lane boundaries from your detector
    if (lane == null) { IJ.showMessage("No lane at click."); return; }

    Band seed = refineBandAtY(imp, lane, y, /*windowPx*/ 20);
    if (seed == null) { IJ.showMessage("No clear peak near click."); return; }

    // Compute normalized coordinate (Rf or MW if ladder calibrated)
    double rfSeed = gel.computeRf(lane, seed); // distance_band / distance_dyefront
    Double mwSeed = gel.isCalibrated() ? gel.model().mwAt(lane, seed) : null;

    // Propagate to other lanes
    List<Band> found = propagateToOtherLanes(imp, lane, seed, rfSeed, mwSeed);

    // Render + record
    gel.overlayBands(found, /*color*/ Color.cyan, /*labelPrefix*/"AI");
    gel.results().addAll(found);
    imp.updateAndDraw();
  }
}
```

### 2) Peak refinement around the click

```
private Band refineBandAtY(ImagePlus imp, Lane lane, int yClick, int windowPx) {
  // Sum across the lane width to get a 1D profile
  float[] profile = Profiles.verticalSum(imp, lane.xStart(), lane.xEnd());
  int y0 = Math.max(0, yClick - windowPx), y1 = Math.min(profile.length-1, yClick + windowPx);

  int peak = Peaks.argmax(profile, y0, y1);          // nearest local maximum
  if (!Peaks.isProminent(profile, peak, /*minProm*/ 0.05f)) return null;

  int left = Peaks.leftValley(profile, peak);
  int right = Peaks.rightValley(profile, peak);
  double apexY = Peaks.subpixelApex(profile, peak);  // quadratic interpolation

  // Integrate minus local baseline (flank means)
  Band b = Quant.integrateBand(imp, lane, left, right);
  b.yApexPx = apexY;
  b.flags.add("assisted");
  return b;
}
```

### 3) Propagate to other lanes by normalized position

```
private List<Band> propagateToOtherLanes(ImagePlus imp, Lane seedLane, Band seed, double rfSeed, Double mwSeed) {
  List<Band> out = new ArrayList<>();
  for (Lane ln : gel.lanes()) {
    // Always keep the clicked one
    if (ln == seedLane) { out.add(seed); continue; }

    // Predict target Y by Rf (or MW)
    int yPred;
    if (mwSeed != null) {
      yPred = (int)Math.round(gel.model().yAtMw(ln, mwSeed)); // inverse of calibration in that lane
    } else {
      yPred = (int)Math.round(gel.distanceFromTop(ln) * rfSeed + gel.wellTopY(ln));
    }

    // Search around prediction
    float[] prof = Profiles.verticalSum(imp, ln.xStart(), ln.xEnd());
    int window = Math.max(8, (int)(0.03 * prof.length)); // ±3% of lane length
    int y0 = Math.max(0, yPred - window), y1 = Math.min(prof.length-1, yPred + window);

    OptionalInt best = Peaks.bestProminent(prof, y0, y1, /*minProm*/0.04f);
    if (best.isEmpty()) continue;

    int pk = best.getAsInt();
    int L = Peaks.leftValley(prof, pk), R = Peaks.rightValley(prof, pk);
    Band b = Quant.integrateBand(imp, ln, L, R);

    // Confidence metrics
    double snr = Quant.snr(prof, pk, L, R);
    double widthRatio = (R-L) / Math.max(1.0, seed.widthPx());
    if (snr < 3.0 || widthRatio < 0.5 || widthRatio > 2.0) continue; // conservative

    b.yApexPx = Peaks.subpixelApex(prof, pk);
    b.confidence = snrToConfidence(snr); // map SNR to 0..1
    b.flags.add("propagated");
    out.add(b);
  }
  return out;
}
```

*(The helper classes `Profiles`, `Peaks`, `Quant`, and your `Lane`/`Band` models follow the patterns you already have for automatic detection. You’re just constraining the search to a local window around the seeded normalized coordinate.)*

------

# ImageJ bits you’ll use under the hood

- **Coordinate mapping**: `ImageCanvas.offScreenX/Y` to convert mouse clicks to image pixels.
- **ROI and Overlay**: build rectangle ROIs for each band; color/label them; add to `Overlay`; optionally `RoiManager`.
- **Plotting** (optional): open a small `PlotWindow` showing the lane profile and the accepted peak for transparency.
- **Results**: `ResultsTable` to append band rows; your PDF/CSV exporter will already use that or your own data model.

------

# Confidence & UX details that make this feel great

- **Tool cursor**: change mouse cursor to a crosshair and status bar text “Assist: click near a band”.
- **Instant feedback**: when the user clicks, highlight the chosen band in the clicked lane immediately (e.g., lime box), then populate the rest in cyan as they’re found.
- **Undo**: keep a small stack so the user can undo the last assisted add.
- **Fallback**: if a lane fails the auto‑propagation criteria, briefly toast “Lane 6 ambiguous—click to confirm there if present,” and wait for a second click there.

------

# Natural‑language glue (optional, nice)

Let the user say things like:

- “Assist pick this band and find it in all lanes.”
   → Your NL intent becomes `{ "action": "assist_pick", "mode":"propagate", "window_px":20 }`
- “Add missing 50 kDa band across lanes.”
   → If calibrated, search by MW; else by Rf of the seeded band.

You already have the executor infrastructure; this is just a new action that arms the click listener and then runs the propagation function above once a click arrives.

------

# Edge cases and how to handle them

- **Smile/curvature**: If you use per‑lane centerlines or have a deskew/straighten step, do the propagation on the **rectified image** (or use per‑lane y‑mapping from the centerline) so the “same band” is truly aligned.
- **Different exposure/contrast per lane**: rely on **prominence** (peak vs local baseline) and **SNR**, not absolute intensity.
- **Very dense bands**: shrink the search window and raise `min_prominence` to avoid false positives, or require a second click in that lane.

------

# Short answer

Yes, using Fiji/ImageJ this is doable and robust: capture a **PointRoi click**, refine to the nearest peak in that lane, translate to a **normalized coordinate**, scan **all other lanes** in a small window with **prominence/SNR** thresholds, and commit **ROIs + results**. The user stays in the loop only when automation is uncertain, which is exactly where humans are brilliant.

If you want, I can bundle these helpers (`Profiles`, `Peaks`, `Quant`, and the `overlayBands` renderer) into compile‑ready classes that match the API you’ve been building.

##### You said:

yes, please

###### ChatGPT said:

Thought for 17s

Awesome — here are the **drop‑in helpers** you asked for: `Profiles`, `Peaks`, `Quant`, and `OverlayRenderer`, plus lean `Lane`/`Band` models so this compiles immediately. They match the assisted‑click flow we designed (seed → refine → propagate → commit).

> Paths assume `plugin/src/main/java/`.

------

### `com/yourco/gel/core/model/Lane.java`

```
package com.yourco.gel.core.model;

import java.util.ArrayList;
import java.util.List;

public final class Lane {
    private final int index1;      // 1-based lane index
    private final int xStart, xEnd; // inclusive bounds in px
    private final int yTop, yBottom;
    public final List<Band> bands = new ArrayList<>();

    public Lane(int index1, int xStart, int xEnd, int yTop, int yBottom) {
        this.index1 = index1; this.xStart = xStart; this.xEnd = xEnd; this.yTop = yTop; this.yBottom = yBottom;
    }
    public int index1()   { return index1; }
    public int xStart()   { return xStart; }
    public int xEnd()     { return xEnd; }
    public int yTop()     { return yTop; }
    public int yBottom()  { return yBottom; }
    public int widthPx()  { return Math.max(1, xEnd - xStart + 1); }
    public int heightPx() { return Math.max(1, yBottom - yTop + 1); }
}
```

### `com/yourco/gel/core/model/Band.java`

```
package com.yourco.gel.core.model;

import java.util.HashSet;
import java.util.Set;

public final class Band {
    public final Lane lane;
    public final int yTop, yBottom;  // band box in image coords
    public final int xStart, xEnd;
    public double yApexPx;           // subpixel apex (image coords)
    public double areaRaw;           // sum of intensities in box
    public double areaBg;            // background estimate (mean_bg * area)
    public double areaCorr;          // areaRaw - areaBg
    public double snr;               // local SNR estimate
    public Double mwKDa;             // optional
    public final Set<String> flags = new HashSet<>();
    public double confidence = 0.0;

    public Band(Lane lane, int xStart, int xEnd, int yTop, int yBottom) {
        this.lane = lane; this.xStart = xStart; this.xEnd = xEnd; this.yTop = yTop; this.yBottom = yBottom;
    }
    public int heightPx() { return Math.max(1, yBottom - yTop + 1); }
    public int widthPx()  { return Math.max(1, xEnd - xStart + 1); }
}
```

------

### `com/yourco/gel/core/analysis/Profiles.java`

```
package com.yourco.gel.core.analysis;

import ij.ImagePlus;
import ij.process.ImageProcessor;

public final class Profiles {
    private Profiles(){}

    /** Sum pixel intensities across xStart..xEnd for every y; returns array length = image height. */
    public static float[] verticalSum(ImagePlus imp, int xStart, int xEnd) {
        ImageProcessor ip = imp.getProcessor();
        int w = ip.getWidth(), h = ip.getHeight();
        int xs = Math.max(0, Math.min(xStart, xEnd));
        int xe = Math.min(w - 1, Math.max(xStart, xEnd));
        float[] out = new float[h];
        for (int y = 0; y < h; y++) {
            double s = 0;
            for (int x = xs; x <= xe; x++) s += ip.getf(x, y);
            out[y] = (float) s;
        }
        return out;
    }

    /** Simple Savitzky–Golay style smoothing: central moving average (odd window). */
    public static float[] smooth(float[] a, int window) {
        if (window < 3 || window % 2 == 0) return a.clone();
        int r = window / 2, n = a.length;
        float[] out = new float[n];
        double sum = 0;
        for (int i = 0; i < n; i++) {
            int i0 = Math.max(0, i - r), i1 = Math.min(n - 1, i + r);
            sum = 0;
            for (int j = i0; j <= i1; j++) sum += a[j];
            out[i] = (float)(sum / (i1 - i0 + 1));
        }
        return out;
    }
}
```

------

### `com/yourco/gel/core/analysis/Peaks.java`

```
package com.yourco.gel.core.analysis;

import java.util.OptionalInt;

public final class Peaks {
    private Peaks(){}

    public static int argmax(float[] v, int y0, int y1) {
        y0 = Math.max(1, y0); y1 = Math.min(v.length - 2, y1);
        int idx = y0; float best = -Float.MAX_VALUE;
        for (int i = y0; i <= y1; i++) if (v[i] > best) { best = v[i]; idx = i; }
        return idx;
    }

    /** Is v[i] a local maximum with prominence >= minProm (fraction of local range)? */
    public static boolean isProminent(float[] v, int i, float minProm) {
        if (i <= 0 || i >= v.length - 1) return false;
        if (!(v[i] > v[i-1] && v[i] >= v[i+1])) return false;
        int L = leftValley(v, i), R = rightValley(v, i);
        float base = 0.5f * (v[L] + v[R]);
        float prom = v[i] - base;
        float localRange = max(v, L, R) - min(v, L, R) + 1e-6f;
        return prom / localRange >= minProm;
    }

    public static int leftValley(float[] v, int i) {
        int k = i;
        while (k > 1 && v[k-1] <= v[k]) k--;
        return k;
    }

    public static int rightValley(float[] v, int i) {
        int k = i;
        while (k < v.length - 2 && v[k+1] <= v[k]) k++;
        return k;
    }

    /** Quadratic interpolation using neighbors to refine apex position. */
    public static double subpixelApex(float[] v, int i) {
        if (i <= 0 || i >= v.length - 1) return i;
        double ym1 = v[i-1], y0 = v[i], yp1 = v[i+1];
        double denom = (ym1 - 2*y0 + yp1);
        if (Math.abs(denom) < 1e-9) return i;
        double delta = 0.5 * (ym1 - yp1) / denom; // in [-0.5,0.5] typically
        return i + delta;
    }

    public static OptionalInt bestProminent(float[] v, int y0, int y1, float minProm) {
        y0 = Math.max(1, y0); y1 = Math.min(v.length - 2, y1);
        int best = -1; float bestProm = 0;
        for (int i = y0; i <= y1; i++) {
            if (!(v[i] > v[i-1] && v[i] >= v[i+1])) continue;
            int L = leftValley(v, i), R = rightValley(v, i);
            float base = 0.5f * (v[L] + v[R]);
            float prom = v[i] - base;
            if (prom <= 0) continue;
            float localRange = max(v, L, R) - min(v, L, R) + 1e-6f;
            float score = prom / localRange;
            if (score >= minProm && score > bestProm) { bestProm = score; best = i; }
        }
        return best >= 0 ? OptionalInt.of(best) : OptionalInt.empty();
    }

    private static float max(float[] v, int a, int b) { float m = -Float.MAX_VALUE; for (int i=a;i<=b;i++) if (v[i]>m) m=v[i]; return m; }
    private static float min(float[] v, int a, int b) { float m =  Float.MAX_VALUE; for (int i=a;i<=b;i++) if (v[i]<m) m=v[i]; return m; }
}
```

------

### `com/yourco/gel/core/analysis/Quant.java`

```
package com.yourco.gel.core.analysis;

import com.yourco.gel.core.model.Band;
import com.yourco.gel.core.model.Lane;
import ij.ImagePlus;
import ij.process.ImageProcessor;

public final class Quant {
    private Quant(){}

    /** Integrate band box minus local background from flank strips. */
    public static Band integrateBand(ImagePlus imp, Lane lane, int bandTopY, int bandBotY) {
        int x0 = lane.xStart(), x1 = lane.xEnd();
        int y0 = clamp(bandTopY + lane.yTop(), lane.yTop(), lane.yBottom());
        int y1 = clamp(bandBotY + lane.yTop(), lane.yTop(), lane.yBottom());
        if (y1 < y0) { int t = y0; y0 = y1; y1 = t; }

        Band band = new Band(lane, x0, x1, y0, y1);
        ImageProcessor ip = imp.getProcessor();

        // Band area sum
        double sum = 0;
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) sum += ip.getf(x, y);
        band.areaRaw = sum;

        // Local background from flanks (above & below), same width, height = 12 px (clamped)
        int flankH = Math.min(12, Math.max(4, (y1 - y0 + 1) / 2));
        double bgSum = 0; int bgN = 0;

        // Above
        int a0 = clamp(y0 - flankH - 4, lane.yTop(), lane.yBottom());
        int a1 = clamp(y0 - 4, lane.yTop(), lane.yBottom());
        for (int y = a0; y <= a1; y++) for (int x = x0; x <= x1; x++) { bgSum += ip.getf(x, y); bgN++; }

        // Below
        int b0 = clamp(y1 + 4, lane.yTop(), lane.yBottom());
        int b1 = clamp(y1 + 4 + flankH, lane.yTop(), lane.yBottom());
        for (int y = b0; y <= b1; y++) for (int x = x0; x <= x1; x++) { bgSum += ip.getf(x, y); bgN++; }

        double bgMean = bgN > 0 ? bgSum / bgN : 0.0;
        band.areaBg = bgMean * (double) band.widthPx() * (double) band.heightPx();
        band.areaCorr = Math.max(0.0, band.areaRaw - band.areaBg);

        // SNR from lane profile around the peak region
        float[] prof = Profiles.verticalSum(imp, x0, x1);
        int py0 = Math.max(lane.yTop(), y0 - flankH);
        int py1 = Math.min(lane.yBottom(), y1 + flankH);
        int peak = argmax(prof, py0, py1);
        band.yApexPx = Peaks.subpixelApex(prof, peak);
        band.snr = snr(prof, peak, py0, py1);

        return band;
    }

    /** Simple local SNR: (peak - mean flanks) / std(flanks). */
    public static double snr(float[] prof, int peak, int y0, int y1) {
        int L0 = Math.max(0, y0), R1 = Math.min(prof.length - 1, y1);
        int flank = Math.max(4, (R1 - L0 + 1) / 6);
        double sum = 0, sum2 = 0; int n = 0;

        // top flank
        int t0 = Math.max(0, peak - 2*flank), t1 = Math.max(0, peak - flank);
        for (int i = t0; i < t1; i++) { sum += prof[i]; sum2 += prof[i]*prof[i]; n++; }
        // bottom flank
        int b0 = Math.min(prof.length-1, peak + flank), b1 = Math.min(prof.length-1, peak + 2*flank);
        for (int i = b0; i <= b1; i++) { sum += prof[i]; sum2 += prof[i]*prof[i]; n++; }

        if (n < 2) return 0.0;
        double mean = sum / n;
        double var = Math.max(1e-6, (sum2 / n) - mean*mean);
        double sd = Math.sqrt(var);
        return (prof[peak] - mean) / sd;
    }

    private static int argmax(float[] v, int a, int b) {
        a = Math.max(0, a); b = Math.min(v.length - 1, b);
        int idx = a; float best = -Float.MAX_VALUE;
        for (int i=a; i<=b; i++) if (v[i] > best) { best = v[i]; idx = i; }
        return idx;
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
```

------

### `com/yourco/gel/core/render/OverlayRenderer.java`

```
package com.yourco.gel.core.render;

import com.yourco.gel.core.model.Band;
import com.yourco.gel.core.model.Lane;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.TextRoi;

import java.awt.*;
import java.util.List;

public final class OverlayRenderer {
    private OverlayRenderer(){}

    /** Build an overlay with lane boxes and band boxes+labels. */
    public static Overlay from(List<Lane> lanes) {
        Overlay ov = new Overlay();
        ov.setStrokeColor(new Color(0, 255, 255)); // cyan bands
        ov.setStrokeWidth(1.5);

        // lanes (light outline)
        for (Lane ln : lanes) {
            Roi laneR = new Roi(ln.xStart(), ln.yTop(), ln.widthPx(), ln.heightPx());
            laneR.setStrokeColor(new Color(0, 255, 0, 100));
            laneR.setStrokeWidth(1.0);
            ov.add(laneR);
        }

        // bands
        for (Lane ln : lanes) {
            int bIndex = 1;
            for (Band b : ln.bands) {
                Roi r = new Roi(b.xStart, b.yTop, b.widthPx(), b.heightPx());
                r.setStrokeColor(new Color(0, 255, 255));
                r.setStrokeWidth(1.5);
                ov.add(r);

                String label = "L" + ln.index1() + " B" + (bIndex++);
                if (b.mwKDa != null) label += String.format(" (%.0f kDa)", b.mwKDa);
                TextRoi tr = new TextRoi(b.xStart + 2, b.yTop + 12, label, new Font("Menlo", Font.PLAIN, 10));
                tr.setStrokeColor(Color.yellow);
                tr.setFillColor(new Color(0,0,0,120));
                ov.add(tr);
            }
        }
        return ov;
    }

    /** Add (or update) a set of bands with a given color/label prefix. */
    public static void addBands(Overlay ov, List<Band> bands, Color color, String labelPrefix) {
        int i = 1;
        for (Band b : bands) {
            Roi r = new Roi(b.xStart, b.yTop, b.widthPx(), b.heightPx());
            r.setStrokeColor(color);
            r.setStrokeWidth(1.8);
            ov.add(r);
            String label = (labelPrefix == null ? "" : labelPrefix + " ") + "L" + b.lane.index1() + " #" + (i++);
            TextRoi tr = new TextRoi(b.xStart + 2, b.yTop + 12, label, new Font("Menlo", Font.PLAIN, 10));
            tr.setStrokeColor(Color.white);
            tr.setFillColor(new Color(0,0,0,140));
            ov.add(tr);
        }
    }
}
```

------

## How to use these in your assisted click flow

- Build lane profiles with `Profiles.verticalSum(imp, lane.xStart(), lane.xEnd())`.
- Refine a clicked band with `Peaks.argmax/leftValley/rightValley/subpixelApex` and integrate via `Quant.integrateBand(...)`.
- Estimate confidence with `Quant.snr(...)`.
- Render everything using `OverlayRenderer.from(lanes)` or `addBands(...)` to add assisted finds in a different color.

Everything is deterministic, pure‑Java, and uses standard ImageJ types (`ImagePlus`, `ImageProcessor`, `Overlay`, `Roi`). You can now wire your **AssistBandTool** to these helpers and it’ll feel snappy and reliable.