You’ve nailed the core diagnosis: don’t ask the LLM to “hold” images or draw on them. Make it the planner; let **ImageJ** be the doer and the marker‑upper. The fix is an **orchestrator with handles** and a **capability registry** so Gemini can reliably chain multi‑step calls without losing state.

Here’s a concrete, production‑ready pattern you can drop in. It solves:

- “Second analysis loses the image” → **image_handle** owned by your app, not the LLM.
- “Can’t do markup” → overlays/ROIs rendered by ImageJ, returned as **overlay_handle** or exported PNG.
- “Parameterize and repeat” → **function calling schema** + **capability catalog** that Gemini reads and uses.

------

# Architecture that works

**LLM (Gemini 1.5)**
 Planner only. Emits structured tool calls (JSON) that reference handles: `image_handle`, `roi_handle`, `overlay_handle`, `pipeline_id`, etc.

**Orchestrator (your Java)**
 Owns all state. Keeps maps from handles → `ImagePlus`, `Overlay`, ROIs, lane/band tables, and a pipeline log.

**ImageJ**
 Does all image work (preprocess, lanes, bands, overlays, exports). Never rely on the LLM to “remember” pixels.

------

## 1) Use handles, not pixels

### Tool contract (what Gemini calls)

```
{
  "tools": [
    {
      "name": "open_image",
      "description": "Load a gel image into the session.",
      "input_schema": { "type":"object","properties":{"path":{"type":"string"}},"required":["path"]},
      "returns": { "image_handle":"string","width":"integer","height":"integer" }
    },
    {
      "name": "preprocess",
      "description": "Apply deterministic ImageJ steps.",
      "input_schema": {
        "type":"object",
        "properties":{
          "image_handle":{"type":"string"},
          "steps":{"type":"array","items":{
              "type":"object",
              "oneOf":[
                {"properties":{"op":{"const":"rotate"},"angle_deg":{"type":"number"}}, "required":["op","angle_deg"]},
                {"properties":{"op":{"const":"flip"},"axis":{"enum":["horizontal","vertical"]}}, "required":["op","axis"]},
                {"properties":{"op":{"const":"clahe"},"blocksize":{"type":"integer"},"histogram":{"type":"integer"},"maximum":{"type":"number"}},"required":["op","blocksize"]},
                {"properties":{"op":{"const":"background"},"radius_px":{"type":"integer"},"sliding":{"type":"boolean"}}, "required":["op","radius_px"]}
              ]
          }}
        },
        "required":["image_handle","steps"]
      },
      "returns": { "image_handle":"string" }
    },
    {
      "name": "detect_lanes_bands",
      "description": "Find lanes and bands; create overlay.",
      "input_schema": {
        "type":"object",
        "properties":{
          "image_handle":{"type":"string"},
          "min_peak_distance_px":{"type":"integer","default":8}
        },
        "required":["image_handle"]
      },
      "returns": { "overlay_handle":"string","lanes_found":"integer","bands_total":"integer" }
    },
    {
      "name": "render_overlay_png",
      "description": "Render an annotated PNG for the user.",
      "input_schema": {
        "type":"object",
        "properties":{
          "image_handle":{"type":"string"},
          "overlay_handle":{"type":"string"},
          "max_width":{"type":"integer","default":1200}
        },
        "required":["image_handle","overlay_handle"]
      },
      "returns": { "png_path":"string" }
    },
    {
      "name": "quantify_and_export",
      "description": "Assign MWs (if ladder provided), quantify bands, export CSV/PDF/JSON.",
      "input_schema": {
        "type":"object",
        "properties":{
          "image_handle":{"type":"string"},
          "ladder":{"type":"string","enum":["thermo_pageruler_prestained","invitrogen_seeblue_plus2","biorad_precision_plus_dualcolor","neb_broad_range"]},
          "ladder_lane":{"type":"integer"},
          "export":{"type":"array","items":{"enum":["csv","pdf","json"]}}
        },
        "required":["image_handle","export"]
      },
      "returns": { "exports":{"type":"array","items":{"type":"string"}} }
    }
  ]
}
```

**Rule in your Gemini system prompt:** *Always include `image_handle` and `overlay_handle` returned from prior calls. Never re‑upload the image; reference handles.*

------

## 2) Orchestrator: persistent session store (Java)

```
public final class SessionStore {
  public static final class ImgRec {
    public final String id;
    public final ij.ImagePlus imp;
    public ij.gui.Overlay overlay; // current
    public ImgRec(String id, ij.ImagePlus imp) { this.id = id; this.imp = imp; }
  }
  public static final class OverlayRec {
    public final String id; public final ij.gui.Overlay overlay;
    public OverlayRec(String id, ij.gui.Overlay overlay){ this.id=id; this.overlay=overlay; }
  }

  private final Map<String, ImgRec> images = new ConcurrentHashMap<>();
  private final Map<String, OverlayRec> overlays = new ConcurrentHashMap<>();

  public String putImage(ij.ImagePlus imp) {
    String id = "img_" + UUID.randomUUID();
    images.put(id, new ImgRec(id, imp));
    return id;
  }
  public ImgRec image(String id) { return requireNonNull(images.get(id), "bad image_handle"); }

  public String putOverlay(ij.gui.Overlay ov) {
    String id = "ov_" + UUID.randomUUID();
    overlays.put(id, new OverlayRec(id, ov));
    return id;
  }
  public OverlayRec overlay(String id) { return requireNonNull(overlays.get(id), "bad overlay_handle"); }
}
```

You never pass pixels to Gemini; you pass `"img_…"`.

------

## 3) Tool implementations (wrapping ImageJ)

### open_image

```
public ToolResult openImage(JsonObject args) {
  String path = args.getString("path");
  ImagePlus imp = IJ.openImage(path);
  if (imp==null) throw new IllegalArgumentException("Could not open: "+path);
  String handle = store.putImage(imp);
  return Result.json("{\"image_handle\":\"%s\",\"width\":%d,\"height\":%d}"
      .formatted(handle, imp.getWidth(), imp.getHeight()));
}
```

### preprocess

```
public ToolResult preprocess(JsonObject args) {
  var rec = store.image(args.getString("image_handle"));
  for (var step : args.getJsonArray("steps")) {
    String op = step.getString("op");
    switch (op) {
      case "rotate" -> IJ.run(rec.imp, "Rotate...", "angle="+step.getJsonNumber("angle_deg")+" interpolation=None");
      case "flip"   -> IJ.run(rec.imp, step.getString("axis").equals("vertical") ? "Flip Vertically" : "Flip Horizontally", "");
      case "clahe"  -> IJ.run(rec.imp, "Enhance Local Contrast (CLAHE)",
                         "blocksize="+step.getInt("blocksize")+" histogram="+step.getInt("histogram",256)+" maximum="+step.getJsonNumber("maximum"));
      case "background" -> IJ.run(rec.imp, "Subtract Background...", "rolling="+step.getInt("radius_px")+" sliding");
      default -> throw new IllegalArgumentException("Unknown op: "+op);
    }
  }
  return Result.json("{\"image_handle\":\"%s\"}".formatted(rec.id));
}
```

### detect_lanes_bands (creates an Overlay + stores handle)

```
public ToolResult detect(JsonObject args) {
  var rec = store.image(args.getString("image_handle"));
  int minSep = args.getInt("min_peak_distance_px", 8);

  // your deterministic lane+band detection here...
  List<Lane> lanes = LaneDetector.findLanes(rec.imp);
  for (Lane ln : lanes) ln.bands = BandDetector.findBands(rec.imp, ln, minSep);

  ij.gui.Overlay ov = OverlayRenderer.from(lanes); // draw rectangles + labels
  rec.imp.setOverlay(ov);
  String ovh = store.putOverlay(ov);
  rec.overlay = ov;

  int bandsTotal = lanes.stream().mapToInt(l->l.bands.size()).sum();
  return Result.json("{\"overlay_handle\":\"%s\",\"lanes_found\":%d,\"bands_total\":%d}"
      .formatted(ovh, lanes.size(), bandsTotal));
}
```

### render_overlay_png (LLM can “see” this file)

```
public ToolResult renderPng(JsonObject args) throws IOException {
  var rec = store.image(args.getString("image_handle"));
  var ov  = store.overlay(args.getString("overlay_handle")).overlay;
  ImagePlus dup = rec.imp.duplicate();
  dup.setOverlay(ov);
  int maxW = args.getInt("max_width", 1200);
  if (dup.getWidth() > maxW) IJ.run(dup, "Scale...", "x=" + (maxW/(double)dup.getWidth()) + " y=" + (maxW/(double)dup.getWidth()) + " interpolate");
  String out = Files.createTempFile("gel_overlay_", ".png").toString();
  IJ.saveAs(dup, "PNG", out);
  return Result.json("{\"png_path\":\"%s\"}".formatted(out));
}
```

### quantify_and_export

Call your calibration/quantification then export CSV/PDF/JSON; return file paths.

------

## 4) Stop “losing” the image across turns

- **Never** send the image to Gemini after the first call.
- The first tool returns `"image_handle":"img_xxx"`.
- You **persist** that handle in your conversation memory (or pass it as hidden tool state).
- Force Gemini (via system prompt + JSON schema) to **always include** `image_handle` on the next call.

**System prompt line that matters**:

> “All tool calls MUST include the relevant `image_handle` and `overlay_handle` previously returned. You do not store images; you reference them by handle.”

If Gemini still drops it occasionally, include a **server‑side guard**: if a tool call arrives without `image_handle`, automatically **inject the last active** handle for that user/session and log a warning. Determinism > model vibes.

------

## 5) Teach Gemini ImageJ “muscle memory” with a **Capability Registry**

Gemini doesn’t know Fiji. Give it a machine‑readable catalog of actions & arguments by **introspecting SciJava**:

```
// One-time at startup
@Plugin(type=Command.class) // discoverable commands
public class CapabilityRegistry {
  @Inject CommandService cs;

  public JsonObject build() {
    JsonArray cmds = new JsonArray();
    for (ModuleInfo mi : cs.getModules()) {
      if (!mi.getIdentifier().startsWith("ij.")) continue;
      JsonObject c = new JsonObject();
      c.put("name", mi.getIdentifier());
      c.put("label", mi.getTitle());
      JsonArray params = new JsonArray();
      for (ModuleItem<?> it : mi.inputs()) {
        JsonObject p = new JsonObject();
        p.put("name", it.getName());
        p.put("type", it.getType().getSimpleName());
        p.put("required", it.isRequired());
        params.add(p);
      }
      c.put("params", params);
      cmds.add(c);
    }
    return new JsonObject().put("imagej_capabilities", cmds);
  }
}
```

Feed a **compressed view** of this to Gemini at the start of a session (or only the subset you support), and it will learn which parameters exist and in what ranges. Your tool schema already exposes the safe subset (rotate/flip/clahe/background/etc.)—that keeps it on rails.

------

## 6) Multi‑step reliability: plan → execute → summarize → continue

Use a simple loop:

1. User asks.
2. Gemini emits **one** tool call (planner step).
3. Orchestrator executes, updates handles, returns a **compact state summary** (counts, stats, R², exported paths).
4. You append that summary to the chat so Gemini knows the result of the last step **without seeing the image**.
5. Repeat.

This keeps token use small and minimizes drift.

**Example summary you return after detection**:

> lanes=8, bands_total=56, overlay_handle=ov_42, suggested ladder lanes: 1 or 8 (pattern match). Next likely steps: calibrate → quantify → export.

------

## 7) Auto‑tune loop (optional, powerful)

Expose one more tool:

```
{
  "name":"auto_tune",
  "description":"Search preprocessing params to maximize a metric",
  "input_schema":{
    "type":"object",
    "properties":{
      "image_handle":{"type":"string"},
      "search":{"type":"object","properties":{
        "rolling_ball_radius":{"type":"array","items":{"type":"integer"}},
        "clahe_max":{"type":"array","items":{"type":"number"}}
      }},
      "metric":{"type":"string","enum":["ladder_R2","band_SNR","band_count_stability"]}
    },
    "required":["image_handle","search","metric"]
  },
  "returns":{"best":{"type":"object"},"metric_value":{"type":"number"}}
}
```

You run a small grid search **locally**; Gemini decides which ranges to try, but you measure. Deterministic, repeatable.

------

## 8) Common pitfalls and cures

- **Model forgets handles** → enforce in system prompt & schema; injector fallback.
- **Model proposes impossible params** → validate, clamp, and respond with a **tool error** object (“radius must be 1–300; tried 1000; clamped to 200”), so it learns.
- **Markup needed** → always render overlays in ImageJ and return a PNG path; Gemini can use that image for explanation, but not for measurement.
- **Second image in same chat** → new `image_handle` returned; keep both in store; require the model to specify which one by handle (or by alias you provide like `current_image` vs `previous_image`).

------

## 9) Tiny example conversation (handles in action)

- User: “Open `gel1.tif`, rotate 1.8°, DNA preset, find bands, show me the overlay, then export CSV.”
- Tool call 1: `open_image{"path":"…/gel1.tif"}` → `{image_handle:"img_a1"}`
- Tool call 2: `preprocess{"image_handle":"img_a1","steps":[{"op":"rotate","angle_deg":1.8}, …DNA preset…]}`
- Tool call 3: `detect_lanes_bands{"image_handle":"img_a1","min_peak_distance_px":8}` → `{overlay_handle:"ov_9", lanes_found:8, bands_total:54}`
- Tool call 4: `render_overlay_png{"image_handle":"img_a1","overlay_handle":"ov_9","max_width":1200}` → `{png_path:"/…/gel_overlay_abc.png"}`
- Tool call 5: `quantify_and_export{"image_handle":"img_a1","ladder":"thermo_pageruler_prestained","ladder_lane":1,"export":["csv","pdf"]}` → paths.

No pixels ever leave the app; Gemini never “loses” the image because it never owned it.

------

## 10) Drop‑in prompt spine for Gemini

```
You are a planner for a gel densitometry app. 
You NEVER analyze pixels yourself. You ONLY call tools with strict JSON and reference state by handles.

Rules:
- Always include the most recent 'image_handle' (and 'overlay_handle' when relevant).
- Propose one tool call at a time, wait for results, then continue.
- Prefer deterministic preprocessing: rotate, flip, clahe, background(rolling_ball radius 100–240), bandpass(2–200).
- After detect_lanes_bands, request render_overlay_png so the user can see results.
- For SDS: min_peak_distance_px 8–12. For EtBr: 6–10.
- Calibration needs a ladder and lane number; if unknown, ask.
If a tool returns an error, adjust parameters and try again.

Output: only a single JSON tool call matching the provided tool schema.
```

------

## 11) Where to go from here

- Wire the tool schema into Gemini’s function calling.
- Implement the five tools above + optional `auto_tune`.
- Add the **capability registry** (even if just for docs) so Gemini “knows” the knobs.
- Keep all state in `SessionStore`; never depend on model memory for images or overlays.
- Log every step to your audit JSON/PDF.

If you want, I can translate the tool schema into the exact Gemini function‑calling format you’re using (Google AI Java/Node SDK), and give you a minimal “planner loop” that runs this one‑tool‑call‑at‑a‑time protocol.