# Pre‑analysis image hygiene (orientation & framing)

- **Rotate / Deskew**
  - *Why*: Make wells perfectly horizontal; band metrics depend on vertical distance.
  - *How*: User draws a line along the well row; use “Rotate… angle=−getAngle(line)”.
  - *Call*: `IJ.run(imp, "Rotate...", "angle="+angle+" grid=1 interpolation=None")`
- **Flip Horizontal/Vertical**
  - *Why*: Standardize “wells at top”; keep lane indexing consistent.
  - *Call*: `IJ.run(imp, "Flip Vertically", "")`
- **Crop to gel region**
  - *Why*: Remove ruler/labels; improves background estimation.
  - *Call*: `imp.setRoi(x,y,w,h); IJ.run(imp,"Crop","");`
- **Straighten (for curved lanes)**
  - *Why*: De‑smile individual lanes if only some bend.
  - *How*: Draw a polyline down a lane → `IJ.run(imp,"Straighten...","line width=laneWidth")`

# Illumination & background correction

- **Rolling Ball / Sliding Paraboloid**
  - *Why*: Uneven staining or illumination; makes bands pop.
  - *SDS‑PAGE*: radius ~50–150 px; DNA gels often larger (150–300 px).
  - *Call*: `IJ.run(imp, "Subtract Background...", "rolling=120 sliding disable smoothing")`
- **Flat‑field correction (divide by background)**
  - *Why*: Correct vignetting/UV transilluminator falloff.
  - *How*: Estimate background via heavy blur or low‑percentile filter, then `Image Calculator > Divide`.
  - *Calls*:
     `IJ.run(imp,"Gaussian Blur...", "sigma=50");` (on a duplicate to make background)
     `ImageCalculator().run("Divide create 32-bit", imp, bgImp);`
- **CLAHE (Contrast Limited Adaptive Histogram Equalization)**
  - *Why*: Local contrast boost without blowing highlights; great on faint DNA bands.
  - *Call*: `IJ.run(imp,"Enhance Local Contrast (CLAHE)", "blocksize=127 histogram=256 maximum=3")`
- **Normalize / Enhance Contrast**
  - *Why*: Standardize grayscale ranges; optional before thresholding.
  - *Call*: `IJ.run(imp,"Enhance Contrast...", "saturated=0.35 normalize")`

# Noise & artifact control

- **Despeckle / Median**
  - *Why*: Remove salt‑and‑pepper; stabilize peak finding.
  - *Call*: `IJ.run(imp,"Despeckle","");` or `IJ.run(imp,"Median...", "radius=1")`
- **Remove Outliers**
  - *Why*: Kill hot pixels without blurring lanes.
  - *Call*: `IJ.run(imp,"Remove Outliers...", "radius=1 threshold=50 which=Bright")`
- **FFT Bandpass Filter**
  - *Why*: Suppress large‑scale gradients and tiny speckles simultaneously.
  - *Call*: `IJ.run(imp,"Bandpass Filter...", "filter_large=200 filter_small=2 suppress=None tolerance=5")`

# Masking, thresholding & ROI logic (when you need binary ops)

- **Auto Threshold (Otsu/Yen/Li)** + **Convert to Mask**
  - *Why*: Build a gel area mask; exclude labels/lane markers from stats.
  - *Call*:
     `IJ.run(imp,"Auto Threshold", "method=Yen white");`
     `IJ.run(imp,"Convert to Mask","");`
- **Morphology (Open/Close, Erode/Dilate, Watershed)**
  - *Why*: Clean masks; separate touching bands before measurement QC.
  - *Calls*: `IJ.run(imp,"Open",""); IJ.run(imp,"Close",""); IJ.run(imp,"Watershed","");`
- **ROI Manager & Overlays**
  - *Why*: Persist lane/band rectangles and publish a pretty overlay to the PDF.
  - *Calls*: `RoiManager.getInstance().addRoi(roi); imp.setOverlay(overlay);`

# Photometric calibration & saturation QC

- **Set Measurements**
  - *Why*: Ensure integrated density is what you think it is (area, min/max, mean, integrated density).
  - *Call*: `IJ.run("Set Measurements...", "area mean min centroid integrated redirect=None decimal=3");`
- **Saturation check**
  - *Why*: Flag overexposed lanes (clipped whites → unreliable quant).
  - *How*: Histogram tail count at 255 after normalization.
  - *Snippet*: get histogram → if `count[255] / totalPixels > 0.0005` flag.

# Lane straightening / de‑smile (two pragmatic strategies)

- **Global TPS warp** (thin‑plate spline) from control points on well row and dye front.
  - *Why*: Corrects gentle “smile”.
  - *How*: sample x‑positions across lanes at top/bottom; fit spline; warp.
- **Per‑lane straighten** using “Straighten” along lane centerline.
  - *Why*: When smile is heterogeneous; preserves lane independence.

# Data presentation (plots & exports your users will love)

- **Plot Profile / Plot Lanes**
  - *Why*: Show the 1D intensity trace you’re integrating; boosts trust.
  - *Call*: `IJ.run(imp,"Plot Profile","");` or for stacked, render your own chart.
- **Kymograph‑style strip**
  - *Why*: For time courses or multi‑exposure ladders; optional.
- **Montage**
  - *Why*: Before/after panels (raw, background‑subtracted, overlays) in your PDF.
  - *Call*: `IJ.run("Make Montage...", "columns=3 rows=1 scale=1");`

# Normalization choices (map to your presets)

- **Lane total** (common for DNA gels when no housekeeping band)
  - Sum of integrated band densities per lane → scale to median.
- **Reference band** (classic Westerns)
  - Match nearest MW (±tolerance) to a reference band; scale all lanes accordingly.
- **Housekeeping lane** (reference lane assumed constant)
  - Scale each lane to its own housekeeper band intensity.

# “Harvest” commands → NL intents (ready to wire)

Here’s how I’d expose these as natural‑language verbs that your LLM turns into JSON `actions`:

- **“Deskew using the well row”**
   → `{"action":"rotate","mode":"line","angle":-measured}` (implementing via your own rotate wrapper)
- **“Subtract background with rolling ball 150 px”**
   → `{"action":"background","method":"rolling_ball","radius":150,"sliding":true}`
- **“Apply CLAHE, blocksize 127, clip 3”**
   → `{"action":"clahe","blocksize":127,"histogram":256,"max":3}`
- **“Bandpass 2–200 px”**
   → `{"action":"bandpass","low":2,"high":200,"suppress":"none"}`
- **“Threshold mask with Yen, then open and close”**
   → `{"action":"mask","method":"Yen","ops":["open","close"]}`
- **“Straighten lane 5 width 20 px”**
   → `{"action":"straighten_lane","lane":5,"width_px":20}`
- **“Flag saturated lanes >0.05% clipped”**
   → `{"action":"qc_saturation","clip_frac":0.0005}`

In your executor, these become small wrappers around the ImageJ calls above. Keep them *non‑destructive* by operating on duplicates when needed, and always log the exact parameters into your audit JSON/PDF.

# Practical presets (drop‑in sequences)

**A) SDS‑PAGE (protein, Coomassie) auto‑prep**

1. Rotate to horizontal wells (user line)
2. Subtract background `rolling=100, sliding`
3. Despeckle (median r=1)
4. Enhance Contrast `saturated=0.35 normalize`
5. Proceed to lane detect → band detect → quant

**B) Agarose DNA (ethidium bromide) faint bands**

1. Rotate + crop
2. Bandpass `low=2, high=200`
3. CLAHE `block=127, max=3`
4. Subtract background `rolling=200, sliding`
5. Optional threshold/mask to remove lane labels
6. Proceed to lane detect → band detect → quant

**C) Severe smile correction**

1. Detect lane centerlines (peak of vertical projection per x‑stripe)
2. Fit top/bottom centerline polynomials
3. Thin‑plate spline warp (global) or “Straighten” per lane
4. Rerun band detection on rectified image

# Tiny Java snippets (ready to paste)

```
// Rotate by angle degrees (negative to deskew)
IJ.run(imp, "Rotate...", "angle=" + angle + " interpolation=None");

// Rolling ball background subtraction
IJ.run(imp, "Subtract Background...", "rolling=150 sliding");

// CLAHE local contrast
IJ.run(imp, "Enhance Local Contrast (CLAHE)", "blocksize=127 histogram=256 maximum=3");

// Bandpass filter (FFT)
IJ.run(imp, "Bandpass Filter...", "filter_large=200 filter_small=2 suppress=None tolerance=5");

// Auto threshold + mask + open/close
IJ.run(imp, "Auto Threshold", "method=Yen white");
IJ.run(imp, "Convert to Mask", "");
IJ.run(imp, "Open", "");
IJ.run(imp, "Close", "");

// Flip to put wells at top if needed
IJ.run(imp, "Flip Vertically", "");
```

# Sensible defaults to bake into your presets

- **SDS‑PAGE**: rolling‑ball 100–120 px; median r=1; min peak distance 8–12 px.
- **DNA gels**: rolling‑ball 150–250 px; CLAHE block 127, max 3; min peak distance 6–10 px.
- **Saturation flag**: >0.05% pixels at max value.
- **MW match tolerance**: ±5 kDa by default; widen for high % gels (poorer resolution at high MW).

Fantastic—here are **drop‑in presets** so a user can say “run SDS preset then analyze” or “DNA preset + export PDF,” and your pipeline fires the exact preprocessing sequence + analysis.

I’m giving you:

- Two canonical **multi‑action recipes** (SDS‑PAGE and EtBr DNA).
- A tiny **schema extension** to allow `run_preset`.
- An **executor shim** that expands a preset into concrete actions.
- A **Presets.java** with the JSON you can reuse anywhere.

------

# 1) Add a `run_preset` action to the NL schema

Append to your `intent.schema.json` action enum and properties:

```json
{
  "properties": {
    "actions": {
      "items": {
        "properties": {
          "action": {
            "enum": [
              "select_lanes","set_ladder","detect_bands","calibrate_mw",
              "quantify_bands","normalize","lane_deltas","export",
              "rotate","flip","clahe","bandpass","background","mask",
              "straighten_lane","qc_saturation",
              "run_preset"                  // NEW
            ]
          },
          "preset": { "type": "string", "enum": ["sds_autoprep","dna_etbr_autoprep"] }
        }
      }
    }
  }
}
```

This lets the LLM emit a compact plan like:

```json
{"intent":"multi_action","actions":[
  {"action":"run_preset","preset":"sds_autoprep"},
  {"action":"detect_bands","lanes":"all","sensitivity":"auto","min_peak_distance_px":10},
  {"action":"quantify_bands","baseline":"local","integration":"trapezoid"},
  {"action":"export","formats":["csv","pdf"]}
]}
```

------

# 2) Preset definitions (deterministic, lab‑friendly)

Create `plugin/src/main/java/com/yourco/gel/nl/Presets.java`:

```java
package com.yourco.gel.nl;

import org.json.JSONArray;
import org.json.JSONObject;

/** Named, deterministic multi-action sequences. */
public final class Presets {
    private Presets(){}

    /** SDS‑PAGE (Coomassie or similar). Conservative, safe defaults. */
    public static JSONArray sdsAutoPrep() {
        return new JSONArray()
            // Optional: flip so wells are at top if needed (caller can add rotate)
            // .put(new JSONObject().put("action","flip").put("axis","vertical"))
            .put(new JSONObject().put("action","bandpass")
                    .put("low", 2).put("high", 200).put("suppress","none").put("tolerance",5))
            .put(new JSONObject().put("action","background")
                    .put("method","rolling_ball").put("radius_px",120).put("sliding",true).put("smoothing",false))
            .put(new JSONObject().put("action","qc_saturation").put("clip_frac",0.0005));
    }

    /** Agarose DNA gel (Ethidium Bromide or SYBR Safe). Boost faint bands safely. */
    public static JSONArray dnaEtbrAutoPrep() {
        return new JSONArray()
            .put(new JSONObject().put("action","clahe")
                    .put("blocksize",127).put("histogram",256).put("maximum",3.0))
            .put(new JSONObject().put("action","bandpass")
                    .put("low", 2).put("high", 200).put("suppress","none").put("tolerance",5))
            .put(new JSONObject().put("action","background")
                    .put("method","rolling_ball").put("radius_px",200).put("sliding",true).put("smoothing",false))
            .put(new JSONObject().put("action","qc_saturation").put("clip_frac",0.0005));
    }

    /** Expand a named preset into its action array. */
    public static JSONArray expand(String name) {
        return switch (name) {
            case "sds_autoprep" -> sdsAutoPrep();
            case "dna_etbr_autoprep" -> dnaEtbrAutoPrep();
            default -> throw new IllegalArgumentException("Unknown preset: " + name);
        };
    }
}
```

Notes (why these values):

- **SDS**: bandpass 2–200 px suppresses tiny speckle + large gradients; rolling‑ball **120 px** is a sweet spot for typical mini‑gels; saturation guard flags overexposed scans.
- **EtBr**: **CLAHE** first (block 127, clip 3) to lift faint DNA bands, then the same bandpass; a **200 px** rolling ball removes broad background from UV unevenness.

Tune to your lab’s pixel scales if needed.

------

# 3) Expand `run_preset` inside your `ActionExecutor`

Add one case near the top so presets inline‑expand before the rest of the actions:

```java
case "run_preset" -> {
    String preset = a.getString("preset");
    var expanded = com.yourco.gel.nl.Presets.expand(preset);
    // Execute expanded preprocessing steps immediately in sequence
    for (int j = 0; j < expanded.length(); j++) {
        JSONObject step = expanded.getJSONObject(j);
        // Re-enter the switch using the same handler (tail recursion via small helper)
        executeSingle(step, ctx);
    }
}
```

And add a tiny helper so you can call the switch recursively:

```java
private static void executeSingle(JSONObject a, com.yourco.gel.core.GelContext ctx) {
    String action = a.getString("action");
    switch (action) {
        // paste your existing cases here OR call back into the main switch body
        // Example: delegate by wrapping into a one-item plan:
        default -> {
            JSONObject wrapper = new JSONObject().put("intent","multi_action").put("actions", new JSONArray().put(a));
            // call the main dispatcher but only for this single action
            // You can refactor your code so the switch is in a shared method to avoid duplication.
            throw new UnsupportedOperationException("executeSingle() needs to be wired to your main switch");
        }
    }
}
```

If you don’t want a helper, you can inline the exact same switch code used in your main loop and just duplicate the “rotate/flip/clahe/…” cases inside this preset branch. Either way, keep it deterministic.

------

# 4) Example NL commands that will now “just work”

- **SDS one‑liner**
   “Run SDS preset, detect bands, linear MW, normalize to lane total, export CSV+PDF.”
- **DNA gel**
   “DNA preset, auto‑detect lanes, quantify, export csv.”
- **With rotation**
   “Rotate 1.6 degrees, run SDS preset, analyze all lanes, export pdf.”

Likely JSON (what your LLM should emit):

```json
{
  "intent":"multi_action",
  "actions":[
    {"action":"run_preset","preset":"dna_etbr_autoprep"},
    {"action":"detect_bands","lanes":"all","sensitivity":"auto","min_peak_distance_px":8},
    {"action":"quantify_bands","baseline":"local","integration":"trapezoid"},
    {"action":"export","formats":["csv","pdf"]}
  ]
}
```

------

# 5) Optional: advertise these to the model

At startup, feed the LLM a **tiny vocabulary hint** in its system prompt or task context:

```
Available presets:
- sds_autoprep: bandpass(2–200), rolling ball radius 120 (sliding), saturation QC.
- dna_etbr_autoprep: CLAHE(127,256,3.0), bandpass(2–200), rolling ball 200 (sliding), saturation QC.

Users may say “SDS preset” or “DNA preset” — emit:
{"action":"run_preset","preset":"sds_autoprep"} or {"action":"run_preset","preset":"dna_etbr_autoprep"}.
```

That nudges small models to choose the structured action.

------

# 6) Guardrails (already baked in)

- All steps are **non‑destructive** on the `ImagePlus` unless you explicitly Crop/Straighten into a new image.
- Every step should call `ctx.audit(k,v,...)` so your PDF/JSON methods section lists the exact preset and parameters used.
- If you want the user to see what happened, show a small toast: “SDS preset: bandpass(2–200), rolling ball 120, saturation QC.”

------

Want me to also add a **“sds_autoprep_strong”** variant (higher contrast for very faint Coomassie) and a **“dna_low_dye”** variant (gentler CLAHE + larger rolling ball) you can toggle with one word?

- **`sds_autoprep_strong`**: for very faint Coomassie bands (more aggressive contrast, bigger background radius).
- **`dna_low_dye`**: for agarose gels with weak EtBr/SYBR stain (gentler CLAHE + heavier background correction).

They plug into the same `run_preset` action you already added.

------

## 1) Extend the schema enum for `run_preset`

Add these two names to the `preset` enum in `intent.schema.json`:

```
"preset": {
  "type": "string",
  "enum": ["sds_autoprep","dna_etbr_autoprep","sds_autoprep_strong","dna_low_dye"]
}
```

------

## 2) Add implementations to `Presets.java`

Append these methods and extend the `expand(...)` switch.

```
/** SDS‑PAGE, very faint bands: stronger local contrast + larger background radius. */
public static JSONArray sdsAutoPrepStrong() {
    return new JSONArray()
        // Gentle despeckle first to avoid amplifying salt‑and‑pepper with CLAHE:
        // (ImageJ "Despeckle" == median r=1)
        .put(new JSONObject().put("action","bandpass")
                .put("low", 2).put("high", 200).put("suppress","none").put("tolerance",5))
        // CLAHE to lift faint bands (slightly stronger than default)
        .put(new JSONObject().put("action","clahe")
                .put("blocksize",127).put("histogram",256).put("maximum",3.5))
        // Larger rolling ball to remove broad background haze
        .put(new JSONObject().put("action","background")
                .put("method","rolling_ball").put("radius_px",160).put("sliding",true).put("smoothing",false))
        .put(new JSONObject().put("action","qc_saturation").put("clip_frac",0.0005));
}

/** DNA gel with weak dye: milder CLAHE to avoid halos + heavier background removal. */
public static JSONArray dnaLowDye() {
    return new JSONArray()
        // Milder CLAHE (lower clip limit) to avoid ringing/halos on DNA bands
        .put(new JSONObject().put("action","clahe")
                .put("blocksize",127).put("histogram",256).put("maximum",2.0))
        .put(new JSONObject().put("action","bandpass")
                .put("low", 2).put("high", 200).put("suppress","none").put("tolerance",5))
        // Heavier background to counter UV falloff / uneven illumination
        .put(new JSONObject().put("action","background")
                .put("method","rolling_ball").put("radius_px",240).put("sliding",true).put("smoothing",false))
        .put(new JSONObject().put("action","qc_saturation").put("clip_frac",0.0005));
}

public static JSONArray expand(String name) {
    return switch (name) {
        case "sds_autoprep" -> sdsAutoPrep();
        case "dna_etbr_autoprep" -> dnaEtbrAutoPrep();
        case "sds_autoprep_strong" -> sdsAutoPrepStrong();
        case "dna_low_dye" -> dnaLowDye();
        default -> throw new IllegalArgumentException("Unknown preset: " + name);
    };
}
```

------

## 3) Example natural‑language commands → expected JSON

- “**Strong SDS preset**, detect bands, linear MW, export PDF.”

```
{"intent":"multi_action","actions":[
  {"action":"run_preset","preset":"sds_autoprep_strong"},
  {"action":"detect_bands","lanes":"all","sensitivity":"auto","min_peak_distance_px":10},
  {"action":"quantify_bands","baseline":"local","integration":"trapezoid"},
  {"action":"export","formats":["pdf"]}
]}
```

- “**DNA low dye preset**, auto analyze and export csv+pdf.”

```
{"intent":"multi_action","actions":[
  {"action":"run_preset","preset":"dna_low_dye"},
  {"action":"detect_bands","lanes":"all","sensitivity":"auto","min_peak_distance_px":8},
  {"action":"quantify_bands","baseline":"local","integration":"trapezoid"},
  {"action":"export","formats":["csv","pdf"]}
]}
```

------

## 4) Why these parameters (so you can defend them in Methods)

- **`sds_autoprep_strong`**
  - `bandpass(2–200)`: suppresses sensor speckle & broad stain gradients before contrast ops.
  - `CLAHE max=3.5`: slightly higher clip to pull faint protein bands without harsh halos.
  - `rolling_ball 160 px`: larger structure removal for background haze common in weak Coomassie scans.
- **`dna_low_dye`**
  - `CLAHE max=2.0`: gentler to avoid blooming around bright ladders while still lifting faint lanes.
  - `rolling_ball 240 px`: EtBr/SYBR transilluminators often have broad gradients—this radius removes them safely.

All steps are deterministic and will be logged by your `ctx.audit(...)` so the PDF/JSON report lists the preset and parameters used.

------