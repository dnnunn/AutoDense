Got it — you want a **fully self‑contained, Mac‑native ImageJ app** that includes a **free local multimodal LLM** (no Ollama or internet required), plus NL (natural‑language) control and the gel analysis stack. Below is a **from‑scratch, step‑by‑step build guide** with code scaffolding, packaging, and notarization. You can follow this verbatim to produce a signed DMG your users can drag‑install and run offline.

------

# 0) What we’re shipping

- A branded **Fiji/ImageJ2 `.app`** with your plugin + JRE inside (true standalone).
- A bundled **local LLM runtime** using **llama.cpp** executables you ship inside the app:
  - **Text LLM** (for natural‑language commands → JSON actions).
  - **Vision LLM** (for optional QC comments on the gel image).
- Your plugin runs **deterministic** lane/band/MW/quantification logic. The LLM only parses user text or produces QC notes.

Disk footprint guide (ballpark): app + JRE (~300–400 MB), + 1 small text model (1–2 GB, Q5 quant) + 1 small vision model (1–3 GB). You can also split “Models Pack” into a second DMG if you need a slimmer core app.

------

# 1) Prereqs on your dev Mac

- macOS 13+ with Xcode CLI tools:
   `xcode-select --install`
- JDK 17 (Temurin or Zulu).
- Maven 3.9+
- Git
- A downloaded **Fiji** (ImageJ) for macOS (arm64 or x86_64).
- **llama.cpp** (to build universal binaries you can redistribute).

> Note: users won’t need any of these; they’ll just install your DMG.

------

# 2) Project layout

Create a workspace folder, e.g. `gel-densitometer/`:

```
gel-densitometer/
  plugin/                       # Maven module: ImageJ2 plugin (Java)
    pom.xml
    src/main/java/...           # core analysis + UI
    src/main/resources/...
  nl/                           # Maven module: LLM client + NL schema/validator
    pom.xml
    src/main/java/...
    src/main/resources/intent.schema.json
  packaging/
    scripts/
      build_llama_universal.sh
      package_app.sh
      sign_and_notarize.sh
    resources/
      Fiji.app/                 # pristine Fiji you’ll customize
      models/                   # gguf models (text + vision) – optional to bundle later
      icons/gel.icns
```

Top‑level `pom.xml` is a simple aggregator for `plugin` and `nl`.

------

# 3) Implement the ImageJ plugin (core analysis)

## 3.1 `plugin/pom.xml` (essentials)

```
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.yourco.gel</groupId>
  <artifactId>gel-densitometer-plugin</artifactId>
  <version>0.1.0</version>
  <properties>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
    <imagej.version>2.14.0</imagej.version>
    <scijava.version>2.0.3</scijava.version>
  </properties>
  <dependencies>
    <dependency>
      <groupId>net.imagej</groupId><artifactId>imagej</artifactId>
      <version>${imagej.version}</version>
    </dependency>
    <dependency>
      <groupId>org.scijava</groupId><artifactId>scijava-common</artifactId>
      <version>${scijava.version}</version>
    </dependency>
    <!-- JSON + schema -->
    <dependency>
      <groupId>org.json</groupId><artifactId>json</artifactId><version>20240303</version>
    </dependency>
    <dependency>
      <groupId>com.networknt</groupId><artifactId>json-schema-validator</artifactId><version>1.4.1</version>
    </dependency>
    <!-- Depend on the NL module -->
    <dependency>
      <groupId>com.yourco.gel</groupId><artifactId>gel-densitometer-nl</artifactId><version>0.1.0</version>
    </dependency>
  </dependencies>
</project>
```

## 3.2 Entry command (drag‑and‑drop + “Auto‑Analyze”)

```
@Plugin(type = Command.class, menuPath = "Plugins>Gel Densitometer>Open & Analyze")
public class OpenAnalyzeCommand implements Command {

  @Parameter(label="Input image", style="open", required=false)
  private File inputFile;

  @Parameter
  private Context context;

  @Override
  public void run() {
    final GelUI ui = new GelUI(context);  // Swing panel with drop zone
    ui.show();                            // DnD opens JPG/TIFF via IJ.openImage()

    // When file is dropped or opened:
    // 1) preprocess (8-bit, invert if needed, rolling ball)
    // 2) detect lanes via vertical projection
    // 3) run NL commands if provided (“set lane 3 as ladder...”), else prompt UI
  }
}
```

## 3.3 Lane detection (deterministic)

```
public final class LaneDetector {
  public static List<Lane> findLanes(ImagePlus imp) {
    // Convert to 8-bit, optional invert, subtract background
    IJ.run(imp, "8-bit", "");
    if (bandsAreLight(imp)) IJ.run(imp, "Invert", "");
    IJ.run(imp, "Subtract Background...", "rolling=100");

    final int w = imp.getWidth(), h = imp.getHeight();
    float[] proj = new float[w];
    for (int x=0; x<w; x++) {
      double s=0;
      for (int y=0; y<h; y++) s += imp.getProcessor().get(x,y);
      proj[x] = (float)s;
    }
    proj = smoothSavGol(proj, 11, 3);
    List<Integer> peaks = findLocalMaxima(proj, /*minSepPx*/ 20, /*prominence*/ 0.1f);
    return expandToLanes(peaks, proj);
  }
}
```

## 3.4 Band detection + quantification

```
public final class BandDetector {
  public static List<Band> findBands(ImagePlus imp, Lane lane) {
    // Sum lane columns to a 1D profile L(y), smooth, find peaks
    FloatProcessor fp = sliceLane(imp, lane);
    float[] profile = verticalSum(fp);
    profile = smoothSavGol(profile, 9, 3);
    List<Integer> peaks = findLocalMaxima(profile, /*minSep*/ 8, /*prom*/ 0.05f);

    // For each peak, refine apex, integrate area - local baseline
    List<Band> bands = new ArrayList<>();
    for (int p : peaks) {
      Band b = integratePeak(profile, p);
      bands.add(b);
    }
    return bands;
  }
}
```

## 3.5 MW calibration (ladder lane → model)

```
public final class Calibrator {
  public static CalibrationModel fit(Lane ladderLane, Ladder ladder) {
    // compute distances from well, measure ladder peaks, pair to known kDa
    double[] d = ladderLane.distances();
    double[] logMw = Arrays.stream(ladder.bandsKda()).map(Math::log10).toArray();
    return RobustLinear.fit(d, logMw); // stores a,b, R2, CI
  }

  public static double assignMw(CalibrationModel m, double distancePx) {
    return Math.pow(10, m.a * distancePx + m.b);
  }
}
```

## 3.6 Normalization + deltas

```
public final class Normalizer {
  public static void laneTotal(List<Lane> lanes) { /* scale each band by lane sum */ }

  public static void referenceBand(List<Lane> lanes, int refLane, double refMwKDa, double tol) {
    // find nearest band to refMwKDa in refLane, compute scaling for all lanes
  }
}

public final class Deltas {
  public static void vsLane(List<Lane> lanes, int refLane, Stat stat) {
    // compute delta/percent/fold/zscore per band by matched MWs (within tolerance)
  }
}
```

## 3.7 Standards library (JSON)

`plugin/src/main/resources/standards.json` (extendable):

```
{
  "standards":[
    {"id":"thermo_pageruler_prestained","name":"Thermo PageRuler Prestained",
     "bands_kda":[170,130,100,70,55,40,35,25,15,10]},
    {"id":"invitrogen_seeblue_plus2","name":"Invitrogen SeeBlue Plus2",
     "bands_kda":[250,150,120,100,80,60,50,40,30,25,20,15,10]}
  ]
}
```

------

# 4) Natural‑language control (fully offline)

We’ll run a **local text LLM** (via llama.cpp) that outputs **strict JSON** matching a schema the executor enforces.

## 4.1 NL schema

`nl/src/main/resources/intent.schema.json`:

```
{
  "$schema":"https://json-schema.org/draft/2020-12/schema",
  "type":"object",
  "required":["intent"],
  "properties":{
    "intent":{"type":"string","enum":["multi_action","needs_disambiguation"]},
    "questions":{"type":"array","items":{"type":"string"}},
    "actions":{
      "type":"array",
      "items":{
        "type":"object",
        "required":["action"],
        "properties":{
          "action":{"type":"string","enum":[
            "select_lanes","set_ladder","detect_bands","calibrate_mw",
            "quantify_bands","normalize","lane_deltas","export"
          ]},
          "lanes":{},
          "lane":{"type":["integer","string"]},
          "ladder_id":{"type":"string"},
          "sensitivity":{"type":["string","number"]},
          "min_peak_distance_px":{"type":"integer"},
          "model":{"type":"string"},
          "dye_front_mode":{"type":"string"},
          "baseline":{"type":"string"},
          "integration":{"type":"string"},
          "mode":{"type":"string"},
          "reference":{"type":"object"},
          "reference_lane":{"type":"integer"},
          "stat":{"type":"string"},
          "formats":{"type":"array","items":{"type":"string"}}
        },
        "additionalProperties":true
      }
    }
  },
  "additionalProperties":false
}
```

## 4.2 NL Java client (spawns local llama.cpp server)

```
public final class NLClient {
  private final int port;
  private final Process server;

  public NLClient(File serverExe, File modelFile) throws IOException {
    this.port = PortFinder.freePort();
    this.server = new ProcessBuilder(
      serverExe.getAbsolutePath(),
      "--model", modelFile.getAbsolutePath(),
      "--host", "127.0.0.1",
      "--port", String.valueOf(port),
      "--n-gpu-layers", "35", "--mlock" // tune for your dev box
    ).inheritIO().start();
    Waiter.until(() -> ping(), 15000, "LLM server failed to start");
  }

  public JSONObject parse(String userText, NLContext ctx) throws Exception {
    JSONObject req = new JSONObject()
      .put("prompt", Prompts.build(userText, ctx))
      .put("temperature", 0.1)
      .put("format", "json"); // llama.cpp: coax model to JSON

    String resp = Http.post("http://127.0.0.1:"+port+"/completion", req.toString());
    JSONObject out = new JSONObject(resp).getJSONObject("completion"); // shape depends on build
    return out;
  }

  public void close() { server.destroyForcibly(); }
}
```

**System prompt** you embed in `Prompts.build()`:

- “You translate user commands for a gel densitometry engine into a JSON plan. Do not analyze images. Output strictly valid JSON per schema. Use 1‑based lane indices. Default missing fields to sensible ‘auto’. If ambiguous, set intent=‘needs_disambiguation’ and include ‘questions’.”

Validate with the JSON schema (networknt validator). If invalid, re‑ask the model with an error hint; if still invalid, show a tiny disambiguation UI.

------

# 5) Vision LLM for QC (optional but bundled = “multimodal”)

Bundle a small **vision‑language GGUF** (e.g., a lightweight LLaVA‑style model) and spawn a **second llama.cpp server** with `--mmproj` and image support. Then, on demand, downscale the gel to 512–768 px width, base64 it, and prompt:

> “Identify common gel issues (overexposure, smiling, streaking, uneven background, bending). Respond in 3 bullet points.”

Always mark these as **non‑binding QC notes** in the PDF.

------

# 6) Build llama.cpp universal binaries (so your app runs on Intel & Apple Silicon)

`packaging/scripts/build_llama_universal.sh`:

```
#!/usr/bin/env bash
set -euo pipefail
rm -rf build && mkdir build
git clone https://github.com/ggerganov/llama.cpp.git build/llama.cpp
pushd build/llama.cpp
make clean
# Build arm64
make LLAMA_SERVER=1 LLAMA_OPENMP=1 -j10
mv bin/llama-server ../../llama-server-arm64
make clean
# Build x86_64
CFLAGS="-arch x86_64" CXXFLAGS="-arch x86_64" LDFLAGS="-arch x86_64" \
make LLAMA_SERVER=1 LLAMA_OPENMP=1 -j10
mv bin/llama-server ../../llama-server-x86_64
popd
lipo -create -output llama-server-universal llama-server-arm64 llama-server-x86_64
mkdir -p ../resources/Fiji.app/Contents/Resources/bin
mv llama-server-universal ../resources/Fiji.app/Contents/Resources/bin/llama-server
chmod +x ../resources/Fiji.app/Contents/Resources/bin/llama-server
```

Do the same if you need a separate **vision server** (or reuse one server with the vision model only for prompts that need it).

------

# 7) Place models inside the app

Put your chosen **text** GGUF at:

```
Fiji.app/Contents/Resources/models/text.gguf
```

…and your **vision** GGUF (and its mmproj, if required) at:

```
Fiji.app/Contents/Resources/models/vision.gguf
Fiji.app/Contents/Resources/models/vision.mmproj.gguf   (if the model requires)
```

Your plugin obtains paths via:

```
File appRoot = new File(System.getProperty("app.dir", new File(".").getAbsolutePath()));
File modelsDir = new File(appRoot, "Contents/Resources/models");
File textModel = new File(modelsDir, "text.gguf");
File visionModel = new File(modelsDir, "vision.gguf");
File serverExe = new File(appRoot, "Contents/Resources/bin/llama-server");
```

> Licensing note: include a LICENSES/ folder and an EULA splash if the model license requires acknowledgment on first run.

------

# 8) Wire the plugin UI (drag‑and‑drop & NL command bar)

- Make a Swing panel with a large drop target (supports `.jpg`, `.jpeg`, `.tif`, `.tiff`).
- Add a **Command Bar** (⌘K). When the user presses Enter, pipe the text to `NLClient.parse()`, validate JSON, then run `ActionExecutor`.

Key NL actions you must implement:

- `set_ladder(lane, ladder_id)`
- `detect_bands(lanes, sensitivity, min_peak_distance_px)`
- `calibrate_mw(model, dye_front_mode)`
- `quantify_bands(baseline, integration)`
- `normalize(mode, reference{lane,mw_kda})`
- `lane_deltas(reference_lane, stat)`
- `export(formats)` → CSV, PDF, JSON

------

# 9) Exports (reproducible science)

**CSV** columns:

```
file,lane,band_index,y_px,distance_px,rf,integrated_density,baseline,area_norm,mw_kda,mw_ci_low,mw_ci_high,flags
```

**PDF**: original image with overlays, standard curve plot with R², table of bands, QC notes (vision LLM), and a **Methods** block listing parameters and the **NL transcript + parsed JSON plan**.

**JSON**: full run state for audit/replay.

------

# 10) Package the standalone `.app`

We’ll start from Fiji and drop in your jars + resources.

**Copy plugin jars**:

```
cp plugin/target/gel-densitometer-plugin-0.1.0.jar \
   nl/target/gel-densitometer-nl-0.1.0.jar \
   packaging/resources/Fiji.app/Contents/java/plugins/
```

**Branding**:

- Replace icon: `Fiji.app/Contents/Resources/JavaAppLauncher.icns` → `gel.icns`
- Edit `Fiji.app/Contents/Info.plist`:
  - `CFBundleName` = `Gel Densitometer`
  - `CFBundleIdentifier` = `com.yourco.gel.densitometer`
  - `CFBundleIconFile` = `gel.icns`
  - Add `LSApplicationCategoryType` = `public.app-category.medical`
- Add your `standards.json`, `intent.schema.json` into `Contents/Resources/`.

**Embed JRE** (if your Fiji doesn’t already): `Contents/PlugIns/jre/` or `Contents/PlugIns/Java.runtime/` depending on Fiji build. Verify `Fiji.app/Contents/MacOS/ImageJ-macosx` launches without system Java.

------

# 11) Codesign + Notarize (so Gatekeeper is happy)

`packaging/scripts/sign_and_notarize.sh`:

```
#!/usr/bin/env bash
APP="Gel Densitometer.app"
IDENTITY="Developer ID Application: YOUR NAME (TEAMID)"
BUNDLE_ID="com.yourco.gel.densitometer"

# 1) Sign embedded binaries (llama-server etc.)
find "$APP" -type f -perm +111 -print0 | while IFS= read -r -d '' f; do
  codesign --force --options runtime --sign "$IDENTITY" "$f"
done

# 2) Sign the .app
codesign --deep --force --options runtime --sign "$IDENTITY" "$APP"
codesign --verify --deep --strict "$APP"

# 3) Notarize
xcrun notarytool submit "$APP" --apple-id "you@appleid.com" \
  --team-id "TEAMID" --password "app-specific-password" --wait

# 4) Staple
xcrun stapler staple "$APP"
```

------

# 12) DMG packaging

`packaging/scripts/package_app.sh` (using `create-dmg` or `appdmg`):

```
create-dmg \
  --volname "Gel Densitometer" \
  --window-pos 200 120 --window-size 640 400 \
  --icon-size 128 \
  --icon "Gel Densitometer.app" 200 200 \
  --app-drop-link 440 200 \
  GelDensitometer.dmg \
  "packaging/resources/"
```

If models are huge, make a second **Models Pack DMG** with just `Contents/Resources/models/…` and an installer script that copies them into the installed app bundle.

------

# 13) First‑run checklist (offline)

- Disable any network calls.
- Start text LLM server on a random free port when the NL bar is first used.
- Only start the vision LLM server when the user opens “QC notes”.
- If a model file is missing, show a clear dialog and a menu item “Install Models…”.

------

# 14) Minimal runnable code you can paste in now

**ActionExecutor** (skeleton):

```
public final class ActionExecutor {
  public static void execute(JSONObject plan, GelContext ctx) {
    String intent = plan.getString("intent");
    if (!"multi_action".equals(intent)) throw new IllegalArgumentException("Unsupported intent");
    JSONArray actions = plan.getJSONArray("actions");
    for (int i=0; i<actions.length(); i++) {
      JSONObject a = actions.getJSONObject(i);
      switch (a.getString("action")) {
        case "set_ladder" -> ctx.setLadder(a.getInt("lane"), a.getString("ladder_id"));
        case "detect_bands" -> ctx.detectBands(a.opt("lanes"), a.opt("sensitivity"), a.optInt("min_peak_distance_px",8));
        case "calibrate_mw" -> ctx.calibrate(a.optString("model","auto"), a.optString("dye_front_mode","auto"));
        case "quantify_bands" -> ctx.quantify(a.optString("baseline","local"), a.optString("integration","trapezoid"));
        case "normalize" -> ctx.normalize(a.getString("mode"), a.optJSONObject("reference"));
        case "lane_deltas" -> ctx.computeDeltas(a.getInt("reference_lane"), a.optString("stat","delta"));
        case "export" -> ctx.export(toStringList(a.getJSONArray("formats")));
        default -> throw new IllegalArgumentException("Unknown action "+a);
      }
    }
  }
}
```

**Starting the text LLM server from inside the app**:

```
public final class LLMServers {
  private static NLClient TEXT;
  public static synchronized NLClient text(File appRoot) {
    if (TEXT != null) return TEXT;
    File exe = new File(appRoot, "Contents/Resources/bin/llama-server");
    File model = new File(appRoot, "Contents/Resources/models/text.gguf");
    try { TEXT = new NLClient(exe, model); }
    catch (Exception e) { throw new RuntimeException("Text LLM failed to start", e); }
    return TEXT;
  }
}
```

**Command bar handler**:

```
String cmd = commandTextField.getText();
NLClient nl = LLMServers.text(appRoot);
JSONObject plan = nl.parse(cmd, NLContext.fromCurrentImage(imp, knownLadders));
JsonSchemas.validate(plan, schema); // throw if invalid
ActionExecutor.execute(plan, gelContext);
AuditTrail.record(cmd, plan, gelContext);
```

------

# 15) Recommended presets (baked into the UI)

- **MW determination**: linear log(MW) vs distance (robust), or poly2 if AIC improves >Δ10.
- **Band quantification**: local baseline ±N px, trapezoidal area, optional Gaussian fits.
- **Normalization**: lane total, reference band (lane X @ MW±tol), housekeeping lane.
- **Lane‑to‑lane deltas**: delta, % change, fold‑change, or z‑score vs control group.

These come as one‑click “recipes” but are always editable; every parameter prints into the report.

------

# 16) Model choices that work well offline

- **Text LLM** (for NL parsing): a small 3–7B instruct model in **GGUF Q5/K‑M** (good accuracy, ~1–2 GB).
- **Vision LLM** (QC notes): a small LLaVA‑style GGUF (~1–3 GB).
- Keep both under `Contents/Resources/models/`. If size is a concern, ship the app without models and provide a **Models Pack** DMG your installer copies in.

------

# 17) Build → Package → Ship (the short script order)

1. `mvn -q -DskipTests=true -f plugin/pom.xml package`
    `mvn -q -DskipTests=true -f nl/pom.xml package`
2. Copy jars into `Fiji.app/Contents/java/plugins/`.
3. Run `packaging/scripts/build_llama_universal.sh`
4. Drop models into `Fiji.app/Contents/Resources/models/`
5. Branding edits (`Info.plist`, icon).
6. `packaging/scripts/sign_and_notarize.sh`
7. `packaging/scripts/package_app.sh` → `GelDensitometer.dmg`

Install on a clean Mac, launch, drop a TIFF, and try:

> ```
> set lane 3 as ladder (thermo_pageruler_prestained), find bands on all lanes, linear mw, normalize to 50 kDa in lane 5, fold-change vs lane 1, export csv+pdf
> ```

The executor should run with overlays, a standard curve plot, and outputs on disk—**no internet, no Ollama**.