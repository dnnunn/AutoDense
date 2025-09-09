# AutoDense UI Overlay Integration: "Drop, See, Ask" Philosophy

> **Doc Meta**
> - **Purpose:** Integration of minimal UI philosophy with complete 7-phase development plan
> - **Scope:** UI simplification overlay that preserves all sophisticated optimization capabilities
> - **Owner:** @davidnunn  
> - **Last-verified:** 2025-09-02

## 🎯 Philosophy Integration

The "Drop, See, Ask" UI overlay provides a **zero-friction front-end** for all the sophisticated AI optimization capabilities we've built in Phases I-VII. This creates a **user-friendly surface** while preserving the **technical depth** underneath.

**Core Philosophy**: 
- **Drop**: Image → immediate auto-analysis → overlay (no setup dialogs)
- **See**: Always show latest results with key metrics 
- **Ask**: Natural language command bar for workflows and optimization

---

## 🏗️ Architecture Overlay

### **How UI Overlay Maps to Development Phases**

| UI Component | Backend Phases | Integration Point |
|-------------|----------------|-------------------|
| **Auto-run analysis** | Phases I-III | Truth preservation + telemetry + parameter independence |
| **Optimize button** | Phase IV | Vision-assist hybrid mode with bounded parameter suggestions |
| **Command bar NL** | Phase V | Natural language intent parsing and feedback integration |
| **Interactive tools** | Phase VI | BandAssist, ColonyAssist for refinement |
| **Workflow requests** | Phase VII | Challenge pack execution via NL commands |

### **Single-Screen Design Architecture**

```
AutoDense UI (Single View)
├── Top Bar
│   ├── Open (file/folder) → Auto-run basic analysis
│   ├── Series → Time-series builder for colonies  
│   ├── Optimize → Phase IV vision-assist triggers
│   └── Export → Bundle results for sharing
├── Left Panel: Runs List
│   └── Recent analyses with status badges
├── Center: Image + Overlay  
│   ├── Always show latest overlay.png
│   └── Hotkeys: L (ladder), I (invert), R (rerun)
├── Right Panel: Summary + Tiny Controls
│   ├── Gel metrics: lanes, bands, R², coverage
│   ├── Colony metrics: counts, blue/white, sizes
│   └── Bounded sliders: sensitivity, spacing
└── Bottom: Command Bar
    ├── Natural language input: "what should I do next?"
    └── Phase V NL parser → workflow execution
```

---

## 🚀 Auto-Run Specifications

### **Immediate Analysis on Drop (No User Input)**

#### **Gel Auto-Run (SDS-PAGE/EtBr)**
1. **Assay Detection**: Heuristic on polarity/channels
2. **Lane Detection**: Run `detect.lanes` with safe defaults
3. **Band Detection**: Run `bands` detection with parameter independence (Phase III)
4. **Ladder MW Mapping**: Try top-K lane candidates, pick highest R²
5. **Overlay Generation**: Lane IDs, band marks, MW labels
6. **Telemetry**: Generate comprehensive flight recorder metrics (Phase II)

```yaml
# Auto-run gel spec (challenge_packs/gel_basic_v1/spec.yaml)
meta:
  name: "gel_basic_v1"
  description: "Immediate gel analysis on drop - no user input"
  trigger: "auto_on_drop"

detect:
  lanes:
    min_peak_distance_frac: 0.035  # Safe default
    prominence_frac: 0.06         # Balanced sensitivity
  baseline:
    method: "percentile"
    window_frac: 0.02            # Conservative baseline
    quantile: 0.10

bands:
  baseline:                      # Completely independent (Phase III)
    method: "percentile" 
    window_frac: 0.015
    quantile: 0.08
  min_peak_distance_px: 14       # Configurable parameter (Phase III)
  prominence_frac: 0.02

# Auto-trigger optimization on fail-gates
optimization:
  auto_trigger_on:
    - lanes_raw: 0
    - bands_raw: 0  
    - baseline_post_med: ~0
    - coverage_total: <0.2

outputs:
  overlay: "overlay.png"         # Always generated
  summary: "run_report.json"     # Phase II telemetry
```

#### **Colony Auto-Run**
1. **Segmentation**: Binary mask contract with watershed
2. **Classification**: Lab colorspace blue/white detection (Phase III fix)
3. **Size Binning**: Automatic size distribution analysis
4. **Filter Chain**: Complete component tracking (Phase II telemetry)
5. **Overlay**: Colony outlines with color codes and size indicators

```yaml
# Auto-run colony spec (challenge_packs/colony_basic_v1/spec.yaml)
meta:
  name: "colony_basic_v1"
  description: "Immediate colony analysis on drop - no user input"
  trigger: "auto_on_drop"

detect:
  min_peak_distance_frac: 0.05
  prominence_frac: 0.08
  threshold_radius: 15
  min_colony_size: 5.0

segmentation:
  threshold:
    method: "Phansalkar"         # Good default for plates
    radius: 20
  min_area_px: 25

color_classification:
  colorspace: "Lab"              # Phase III fix
  method: "rule_based"
  blue_threshold_b: -4.0
  generate_histogram: true       # Phase II telemetry

# Auto-trigger optimization on fail-gates  
optimization:
  auto_trigger_on:
    - mask_not_binary: true
    - final_count: "<<components_raw"
    - classifier_applied: false

outputs:
  overlay: "overlay.png"         # Color-coded colonies
  summary: "run_report.json"     # Phase II filter chain ledger
```

---

## 📊 Time-Series System for Colony Workflows

### **Series Builder Integration**

**Problem Solved**: Colony time-series workflows need multiple images with temporal ordering and alignment.

**Solution**: Series manifest system with drag-drop interface and automatic temporal ordering.

#### **Series Manifest Format**
```json
{
  "series_id": "plate_42_growth_study",
  "created_utc": "2025-09-02T11:05:00Z",
  "metadata": {
    "plate_id": "plate_42",
    "experiment": "growth_kinetics",
    "strain": "E_coli_DH5a"
  },
  "items": [
    {
      "path": "/data/plates/plate_42_t0.jpg",
      "timestamp": "2025-08-29T10:00:00Z", 
      "timepoint_hours": 0,
      "phash": "f3a1b2...",
      "auto_analysis": {
        "colony_count": 15,
        "blue_count": 0,
        "white_count": 15
      }
    },
    {
      "path": "/data/plates/plate_42_t1.jpg",
      "timestamp": "2025-08-30T10:00:00Z",
      "timepoint_hours": 24,
      "phash": "e0cc91...",
      "auto_analysis": {
        "colony_count": 28,
        "blue_count": 5,
        "white_count": 23
      }
    }
  ],
  "processing_status": "ready",
  "alignment_quality": null,
  "tracking_results": null
}
```

#### **Series Builder UI Flow**
1. **Click Series** → Drawer opens with drag-drop area
2. **Add Images** → Auto-deduplicate by perceptual hash, warn on duplicates
3. **Auto-order** → Sort by EXIF timestamp → filename → mtime
4. **Auto-analyze each** → Run colony_basic_v1 on each image
5. **Process Series** → Execute colony_timeseries_v1 with alignment and tracking

#### **Implementation Components**
```python
# autodense_autotune/series/manifest_manager.py
class SeriesManifestManager:
    def create_manifest(self, series_id: str, images: List[str]) -> SeriesManifest
    def add_images(self, manifest: SeriesManifest, new_images: List[str]) -> SeriesManifest
    def deduplicate_by_hash(self, images: List[str]) -> List[str]
    def extract_timestamps(self, image_path: str) -> datetime
    def auto_analyze_timepoint(self, image_path: str) -> Dict[str, Any]

# autodense_autotune/series/processor.py  
class TimeSeriesProcessor:
    def process_series(self, manifest: SeriesManifest) -> TimeSeriesResults
    def align_plates(self, images: List[str]) -> AlignmentResults
    def track_colonies(self, aligned_images: List[str]) -> TrackingResults
    def fit_growth_curves(self, tracks: List[ColonyTrack]) -> GrowthResults
```

---

## 🎛️ Bounded Controls Integration

### **Tiny Controls → Phase IV Parameter Patches**

The minimal UI provides just a few bounded controls that map to safe parameter adjustments:

| Control | Parameter Patch | Allowlist Bounds | Effect |
|---------|----------------|------------------|--------|
| **Sensitivity ↑** | `bands.prominence_frac: -10%` | [0.02, 0.12] | More sensitive band detection |
| **Band spacing ↓** | `bands.min_peak_distance_px: -20%` | [6, 18] | Closer band detection |
| **Shading correction** | `pre.clahe.enabled: true` | [true, false] | CLAHE preprocessing |
| **Lab color** | `color_classification.colorspace: "Lab"` | ["Lab", "RGB"] | Better blue/white separation |

### **Quality Gate Integration**
All control changes trigger **bounded rerun** with Phase IV acceptance criteria:
- **Coverage improvement** OR **stability maintenance**
- **No physics degradation** (ladder R², geometry)
- **Parameter bounds respected** (allowlist validation)

---

## 🗣️ Command Bar → Workflow Integration

### **Natural Language → Challenge Pack Execution**

The command bar uses **Phase V NL parser** to translate user requests into workflow execution:

| User Request | Intent Recognition | Challenge Pack | Auto-execution |
|--------------|-------------------|----------------|----------------|
| *"Quantify protein with log-linear"* | `protein_quant` | `protein_quant_v1` | Requires sample sheet |
| *"Compare treatment vs control"* | `lane_compare` | `lane_compare_v1` | Requires sample sheet |
| *"Track colonies across days"* | `colony_timeseries` | `colony_timeseries_v1` | Uses series manifest |
| *"More sensitive on bands"* | `optimize_params` | Phase IV patch | Immediate rerun |
| *"Open colony assist"* | `colony_assist` | `colony_assist_v1` | Interactive mode |

### **Sample Sheet Integration**
For workflows requiring experimental design:
- **Auto-detect**: Look for CSV files in same directory
- **Smart prompts**: "I need a sample sheet with columns: lane, condition, replicate"
- **Template generation**: Create sample sheet templates for common workflows

---

## 📱 Implementation Priority for UI Overlay

### **Phase I: Core Auto-Run (Week 1)**
1. **Basic challenge packs**: `gel_basic_v1`, `colony_basic_v1`
2. **Auto-run triggers**: File drop → immediate analysis
3. **Overlay generation**: Always show latest results
4. **Fail-gate detection**: Auto-suggest optimize on problems

### **Phase II: Series Builder (Week 2)**  
1. **Manifest system**: Time-series image management
2. **Drag-drop interface**: Multi-image ingestion
3. **Auto-ordering**: Temporal sequence detection
4. **Series processing**: Colony tracking workflow integration

### **Phase III: Command Bar Integration (Week 3)**
1. **NL parser integration**: Phase V intent recognition
2. **Workflow dispatch**: Challenge pack execution
3. **Sample sheet handling**: Auto-detect and template generation
4. **Interactive modes**: BandAssist, ColonyAssist integration

### **Phase IV: Polish & Optimization (Week 4)**
1. **Bounded controls**: Slider integration with parameter patches
2. **Keyboard shortcuts**: Power user efficiency
3. **Export system**: One-click result bundling
4. **Performance optimization**: Fast overlay updates

---

## 🛡️ Architecture Preservation

### **All Phase Benefits Maintained**
- ✅ **Phase I-III**: Truth preservation, telemetry, parameter independence
- ✅ **Phase IV**: Vision-assist triggered by optimize button
- ✅ **Phase V**: Natural language command processing
- ✅ **Phase VI**: Interactive assist tools available
- ✅ **Phase VII**: Complete workflow library accessible

### **UI Simplicity Principles**
- **Zero-friction ingest**: No setup dialogs ever
- **Immediate feedback**: Always show something useful
- **Progressive disclosure**: Advanced features available but not prominent
- **Context preservation**: All actions apply to current run
- **Honest defaults**: Safe parameters that usually work

---

## 📊 Success Criteria

### **User Experience Metrics**
- **Time to first result**: <10 seconds from drop to overlay
- **Learning curve**: New users productive in <5 minutes
- **Workflow execution**: Common analyses via single NL command
- **Series processing**: Multi-timepoint colony analysis in one flow

### **Technical Integration Metrics**
- **Auto-run success rate**: >95% produce reasonable overlays
- **Fail-gate accuracy**: Correctly identify when optimization needed
- **NL parser accuracy**: >95% correct intent recognition
- **Series alignment**: <5px RMS error for colony tracking

---

## 🎯 Final Integration Summary

The "Drop, See, Ask" UI overlay provides a **dramatically simplified** user experience while preserving **all sophisticated capabilities**:

1. **Drop**: Auto-run basic analysis using Phases I-III foundation
2. **See**: Real-time overlay updates with Phase II telemetry integration  
3. **Ask**: Natural language workflows using Phase V parser + Phase VII challenge packs

**Key Innovation**: **Zero-friction surface** with **full-depth underneath**. Scientists get immediate results but can access any level of sophistication through simple natural language requests.

**Maintained Philosophy**: **"Gemini advises, AutoDense measures, users guide, science decides"** - now with **"Drop, See, Ask"** user experience overlay.

This completes the integration of the minimal UI philosophy with our complete 7-phase development plan, creating a system that is both **scientifically rigorous** and **delightfully simple** to use.