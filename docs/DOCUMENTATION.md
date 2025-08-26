# AutoDense Documentation

**AI-Powered Gel Densitometry Analysis with Natural Language Control**

Version: 0.1.0  
Last Updated: January 2025

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Core Features](#core-features)
4. [Natural Language Interface](#natural-language-interface)
5. [Image Preprocessing](#image-preprocessing)
6. [Molecular Weight Standards](#molecular-weight-standards)
7. [Workflow Recording System](#workflow-recording-system)
8. [Scientific Methodologies](#scientific-methodologies)
9. [API Reference](#api-reference)
10. [Installation & Setup](#installation--setup)
11. [Examples & Tutorials](#examples--tutorials)
12. [Troubleshooting](#troubleshooting)

---

## Overview

AutoDense is a comprehensive gel densitometry analysis platform that combines proven scientific algorithms from Fiji ImageJ with modern AI-powered natural language control. The system enables researchers to perform sophisticated gel analysis through conversational interaction with a local AI assistant.

### Key Capabilities

- **Offline AI Control**: Local LLM (Gemma 3 4B) with vision capabilities
- **Proven Algorithms**: University of Tokyo quantification methods
- **Comprehensive Standards**: 13 protein + 10 DNA molecular weight markers
- **Workflow Recording**: Create and share custom analysis protocols
- **Full Stack Preprocessing**: 50+ Fiji image processing operations
- **Natural Language**: Conversational control of complex analysis workflows

### Supported Gel Types

- **SDS-PAGE**: Protein separation and quantification
- **Agarose DNA**: Fragment size analysis and quantification  
- **Western Blots**: Immunoblot analysis and normalization
- **Native Gels**: Non-denaturing protein analysis
- **Fluorescent Gels**: Multi-channel analysis support

---

## Architecture

AutoDense is built as a Maven multi-module project with three core components:

### Core Modules

```
autodense/
├── plugin/           # ImageJ2 plugin (analysis engine)
├── nl/              # Natural language processing
├── packaging/       # macOS app bundle creation
└── workflows/       # User workflow library
```

### Component Overview

**Plugin Module** (`autodense-plugin`)
- Main analysis engine and UI components
- Fiji algorithm implementations
- Molecular weight standard database
- Workflow recording and playback

**NL Module** (`autodense-nl`) 
- Local LLM server management
- Natural language to JSON conversion
- Schema validation and error handling
- Multimodal image analysis support

**Packaging Module**
- macOS application bundle creation
- LLM binary integration (llama.cpp)
- Code signing and notarization
- Self-contained distribution

---

## Core Features

### 1. AI-Powered Analysis

**Local LLM Integration**
- **Model**: Gemma 3 4B Instruction-Tuned with Vision
- **Infrastructure**: llama.cpp with Metal GPU acceleration  
- **Response Time**: ~650ms for natural language processing
- **Offline Operation**: No internet connection required

**Natural Language Understanding**
```bash
User: "Apply SDS preset, detect bands, quantify using sides method"
System: Executes 4-step workflow with scientific parameters
```

### 2. Scientific Algorithm Foundation

**Quantification Formula** (University of Tokyo)
```
signal = area × (mean_intensity - background_intensity)
```

**Background Methods**
- **All**: Uniform expansion (universal)
- **Top/Bottom**: Horizontal sampling (protein bands)  
- **Sides**: Vertical sampling (gel lanes) - RECOMMENDED

**Statistical Methods**
- **Median**: Robust to noise (RECOMMENDED)
- **Mean**: Faster computation, clean gels

### 3. Comprehensive Preprocessing

**Image Correction**
- Rotation and geometric correction
- Contrast enhancement (CLAHE)
- Background subtraction (rolling ball)
- Noise reduction (bandpass filtering)
- Quality control (saturation detection)

**Laboratory Presets**
- **SDS AutoPrep**: Standard protein gel processing
- **DNA EtBr AutoPrep**: DNA gel with fluorescent dyes
- **Strong Enhancement**: Very faint bands
- **Low Dye**: Weak staining protocols

---

## Natural Language Interface

### Command Categories

**Analysis Control**
```bash
"Set PageRuler Plus protein ladder in lane 1"
"Detect bands with high sensitivity" 
"Quantify all bands using median background"
"Normalize to lane total"
"Export results as CSV and PDF"
```

**Image Processing**
```bash
"Rotate gel 2.5 degrees clockwise"
"Apply CLAHE with blocksize 127"
"Remove background with rolling ball 150 pixels"
"Check for saturation"
```

**Workflow Management**
```bash
"Start recording workflow called 'Standard Protein Analysis'"
"Stop recording and save workflow"
"Run my saved workflow 'DNA Fragment Analysis'"
"Show all available workflows"
```

### Response Format

All natural language commands are converted to structured JSON:

```json
{
  "intent": "multi_action",
  "actions": [
    {
      "action": "set_ladder",
      "marker_id": "thermo_pageruler_plus_prestained",
      "marker_type": "protein",
      "lane": 1
    }
  ]
}
```

---

## Image Preprocessing

### Core Operations

**Geometric Corrections**
- `rotate`: Fix gel orientation (angle in degrees)
- `flip`: Standardize well position (horizontal/vertical)  
- `crop`: Remove labels and rulers
- `straighten`: De-smile curved lanes

**Contrast & Enhancement**
- `clahe`: Local contrast enhancement (faint bands)
- `background`: Rolling ball or paraboloid subtraction
- `bandpass`: Remove speckle and gradients
- `mask`: Binary region selection

**Quality Control**
- `qc_saturation`: Detect overexposed regions
- Parameter validation and safety checks
- Scientific methodology logging

### Laboratory Presets

**SDS-PAGE Standard**
```json
{
  "preset": "sds_autoprep",
  "steps": [
    {"action": "bandpass", "low": 2, "high": 200},
    {"action": "background", "method": "rolling_ball", "radius_px": 120},
    {"action": "qc_saturation", "clip_frac": 0.0005}
  ]
}
```

**DNA Gel Enhanced**
```json
{
  "preset": "dna_etbr_autoprep", 
  "steps": [
    {"action": "clahe", "blocksize": 127, "maximum": 3.0},
    {"action": "bandpass", "low": 2, "high": 200},
    {"action": "background", "method": "rolling_ball", "radius_px": 200}
  ]
}
```

### Parameter Ranges

| Parameter | Range | Default | Purpose |
|-----------|-------|---------|---------|
| CLAHE Blocksize | 32-512 | 127 | Local contrast window |
| CLAHE Maximum | 0.5-10.0 | 3.0 | Contrast limit |
| Bandpass Low | 1-50 | 2 | Small feature removal |
| Bandpass High | 50-500 | 200 | Large gradient removal |
| Rolling Ball Radius | 10-500 | 120 | Background structure size |

---

## Molecular Weight Standards

### Protein Standards Database

**Default: Thermo PageRuler Plus (26619)**
- Range: 250-10 kDa (9 bands)
- Color: 3-color (blue, orange, green)
- Reference: 70 kDa enhanced band

**Complete Collection (13 standards)**
```
Bio-Rad Precision Plus Series:
- All Blue (1610373): 10 blue bands, 25/50/75 kDa references
- Dual Color (1610374): Blue + pink reference bands
- Kaleidoscope (1610375): Multicolor visualization

Thermo Fisher Series:
- PageRuler Plus (26619): 3-color, 250-10 kDa [DEFAULT]
- Spectra Multicolor (26634): 4-color, 260-10 kDa  
- HiMark (LC5699): High MW, 460-31 kDa
- BenchMark (10748010): 15-band, 220-10 kDa
- Novex Broad (LC5800): 12-band, 260-3.5 kDa

NEB & Others:
- NEB Color (P7712): 12-band multicolor
- Proteintech (PL00001): 11-band, 180-10 kDa
```

### DNA Standards Database

**Default: NEB 1kb Ladder (N3232)**
- Range: 10-0.5 kb (12 bands)
- Reference: 3 kb enhanced band
- Most popular choice globally

**Complete Collection (10 standards)**
```
NEB Series:
- 1kb Ladder (N3232): 10-0.5 kb [DEFAULT]
- 100bp Ladder (N3231): 1517-100 bp
- 1kb Plus (N3200): 19-band, 10kb-100bp
- Lambda/HindIII (N3012): 23.1kb-125bp

Thermo GeneRuler Series:
- 1kb (SM0311): 13-band, 10-0.25 kb
- 100bp (SM0241): 10-band, 1031-100 bp
- Mix (SM0331): 21-band, 10kb-80bp

Bio-Rad Quick-Load Series:
- 1kb (1708355): Ready-to-load format
- 100bp (1708354): Enhanced visibility

Invitrogen:
- TrackIt 1kb Plus (10488058): Extended range, 12kb-100bp
```

### Standard Selection

**Natural Language Commands**
```bash
"Use Bio-Rad All Blue for protein analysis"
"Set NEB 1kb ladder for DNA fragments"  
"I need a marker for proteins up to 400 kDa" → Suggests HiMark
"What's the best ladder for PCR products 200-3000 bp?" → Suggests appropriate options
```

**Automatic Selection**
- Protein gels: PageRuler Plus (default)
- DNA gels: NEB 1kb (default)
- High MW proteins: HiMark recommendation
- Small fragments: 100bp ladder recommendation

---

## Workflow Recording System

### Overview

The workflow recording system allows users to create custom, reusable analysis protocols by demonstrating the desired sequence through natural language interaction.

### Recording Process

**1. Start Recording**
```bash
User: "Start recording a workflow called 'Standard Western Blot Analysis'"
System: 🔴 Recording started - captures all subsequent actions
```

**2. Perform Analysis**
```bash
User: "Set Bio-Rad protein ladder in lane 1"
System: ✅ Action recorded (Step 1)

User: "Apply strong SDS preset for faint bands"  
System: ✅ Action recorded (Step 2)

User: "Detect bands with high sensitivity"
System: ✅ Action recorded (Step 3)

User: "Quantify using sides background with 5-pixel expansion"
System: ✅ Action recorded (Step 4)
```

**3. Stop and Save**
```bash
User: "Stop recording and save the workflow"
System: ✅ Saved 'Standard Western Blot Analysis' (4 steps, ~32s estimated time)
```

### Workflow Playback

**Simple Execution**
```bash
User: "Run my saved workflow 'Standard Western Blot Analysis'"
System: 🎬 Executing workflow... (4 steps)
System: ✅ Workflow completed successfully
```

**Workflow Management**
```bash
User: "Show me all my workflows"
System: Lists all saved workflows with details

User: "Delete the workflow called 'Old Analysis'"  
System: ✅ Workflow deleted

User: "Find workflows that use CLAHE preprocessing"
System: Shows workflows containing CLAHE steps
```

### Workflow Structure

**Stored Format**
```json
{
  "id": "uuid-12345",
  "name": "Standard Western Blot Analysis",
  "description": "Routine western blot quantification protocol",
  "author": "researcher",
  "created_at": "2025-01-15T10:30:00",
  "steps": [
    {
      "step": 1,
      "action": {
        "action": "set_ladder",
        "marker_id": "biorad_precision_plus_all_blue",
        "marker_type": "protein",
        "lane": 1
      },
      "user_prompt": "Set Bio-Rad protein ladder in lane 1",
      "context": "Image: 512x512, History: 0 entries",
      "timestamp": "2025-01-15T10:31:15"
    }
  ],
  "metadata": {
    "gel_type": "protein",
    "estimated_duration": 32
  }
}
```

### Advanced Features

**Workflow Discovery**
```bash
"Find DNA analysis workflows" → Search by keywords
"Show workflows using Bio-Rad markers" → Search by parameters
"List workflows by execution time" → Sort by complexity
```

**Import/Export**
```bash
"Export all workflows" → Creates shareable JSON file
"Import workflows from file" → Loads shared protocols
```

**Analytics**
```bash
"Show workflow statistics" → Usage and complexity metrics
```

### Use Cases

**Laboratory Standardization**
- Create SOPs for routine analyses
- Share protocols between team members
- Ensure consistent methodology

**Training & Education**
- New users learn from expert workflows
- Step-by-step guided analysis
- Best practice documentation

**Quality Control**
- Reproducible analysis protocols
- Parameter consistency
- Audit trail for regulatory compliance

---

## Scientific Methodologies

### Quantification Algorithm

**Core Formula** (Kenji OHGANE, University of Tokyo)
```
Band Signal = Band Area × (Mean Band Intensity - Background Intensity)
```

**Background Region Selection**
- **ALL**: Expands uniformly around ROI (universal compatibility)
- **TOP_BOTTOM**: Samples above/below ROI (horizontal protein bands)
- **SIDES**: Samples left/right of ROI (vertical gel lanes, RECOMMENDED)

**Statistical Methods**
- **MEDIAN**: More robust to noise and outliers (RECOMMENDED)
- **MEAN**: Standard average, faster computation

### Validation Studies

**Academic Foundation**
- Based on Image Studio Lite algorithms
- Validated against University of Tokyo research
- 10+ years of peer-reviewed methodology
- Compatible with Nature/Science publication standards

**Parameter Recommendations**
```
Standard Protein Gels:
- Background: SIDES + MEDIAN
- Expansion: 3-5 pixels
- Reset scale: true (reproducible results)

Noisy Gels:
- Background: ALL + MEDIAN  
- Expansion: 5+ pixels
- Consider preprocessing (bandpass filter)

High-Quality Gels:
- Background: SIDES + MEAN
- Expansion: 3 pixels
- Faster processing
```

### Multi-Channel Analysis

**Channel Weighting Options**
```
Red Fluorophore: R=1.0, G=0.0, B=0.0
Green Fluorophore: R=0.0, G=1.0, B=0.0
Blue Stain: R=0.0, G=0.0, B=1.0
Luminance (ImageJ): R=0.299, G=0.587, B=0.114
Equal RGB: R=0.33, G=0.33, B=0.33
```

---

## API Reference

### Core Classes

**ActionExecutor**
```java
// Execute analysis plan
public static void execute(JSONObject plan, GelContext ctx)
public static void execute(JSONObject plan, GelContext ctx, String userPrompt)
```

**MolecularWeightStandards**
```java
// Standard database access
public static List<ProteinStandard> getProteinStandards()
public static List<DNAStandard> getDNAStandards()
public static ProteinStandard getDefaultProteinStandard()
public static List<ProteinStandard> findProteinStandardsByRange(double min, double max)
```

**WorkflowManager**
```java
// Workflow persistence
public void saveWorkflow(RecordedWorkflow workflow)
public RecordedWorkflow loadWorkflow(String name)
public List<RecordedWorkflow> listAllWorkflows()
public List<RecordedWorkflow> searchWorkflows(String keyword)
```

**ImagePreprocessor**
```java
// Image processing operations
public static ImagePlus rotate(ImagePlus imp, double angle)
public static ImagePlus clahe(ImagePlus imp, int blocksize, int histogram, double maximum)
public static ImagePlus subtractBackground(ImagePlus imp, String method, int radius, boolean sliding, boolean smoothing)
public static SaturationResult checkSaturation(ImagePlus imp, double clipFraction)
```

### JSON Schema

**Action Structure**
```json
{
  "intent": "multi_action",
  "actions": [
    {
      "action": "string",
      "parameters": "varies by action"
    }
  ]
}
```

**Common Parameters**
```json
{
  "lanes": ["1", "2", "3"],
  "marker_id": "thermo_pageruler_plus_prestained",
  "marker_type": "protein",
  "background_region": "sides",
  "background_method": "median",
  "expansion_pixels": 3,
  "preset": "sds_autoprep"
}
```

---

## Installation & Setup

### System Requirements

**macOS Application Bundle**
- macOS 10.15+ (Catalina or later)
- Apple Silicon or Intel processor
- 8GB RAM (16GB recommended)
- 5GB disk space (including models)

**Java Development**
- Java 17 or later
- Maven 3.6+
- ImageJ 2.14.0+

### Build Instructions

**Complete Build**
```bash
cd autodense/
mvn clean package
./packaging/scripts/build_llama_universal.sh
./packaging/scripts/package_app.sh
```

**Module-Specific**
```bash
# Plugin module only
cd autodense/plugin/
mvn clean package

# NL module only  
cd autodense/nl/
mvn clean package
```

### Configuration

**Model Setup**
- Gemma 3 4B model: `Contents/Resources/models/`
- Vision projection: `Contents/Resources/models/mmproj/`
- LLM server binary: `Contents/Resources/bin/llama-server`

**Data Directories**
- Workflows: `~/.autodense/workflows/`
- Standards: `plugin/src/main/resources/standards.json`
- Presets: Built into `Presets.java`

### Testing Protocol

**Start LLM Server**
```bash
# Background server startup
llama-server --model gemma-3-4b-it.gguf --port 8080
```

**Run Tests**
```bash
# Full preprocessing test
java TestFullStackPreprocessing

# Molecular weight markers
java TestMolecularWeightMarkers

# Workflow recording
java TestWorkflowRecording

# Fiji integration  
java TestFijiIntegration
```

---

## Examples & Tutorials

### Basic Protein Gel Analysis

**Step-by-Step Workflow**
```bash
1. "Load protein gel image"
2. "Set PageRuler Plus ladder in lane 1"
3. "Run SDS preset for preprocessing" 
4. "Detect bands with auto sensitivity"
5. "Quantify bands using sides method"
6. "Normalize to lane total"
7. "Export results as CSV and PDF"
```

**Natural Language Version**
```bash
User: "Analyze this protein gel with standard settings"
System: Executes complete 7-step workflow automatically
```

### DNA Fragment Analysis

**PCR Product Sizing**
```bash
1. "Set NEB 1kb DNA ladder in lane 1"
2. "Apply DNA preset with CLAHE enhancement"
3. "Detect fragments in lanes 2-8"
4. "Size fragments using ladder calibration"
5. "Export fragment sizes as spreadsheet"
```

**Workflow Recording**
```bash
1. "Start recording 'PCR Analysis Protocol'"
2. [Perform above steps]
3. "Stop recording and save"
4. Future use: "Run PCR Analysis Protocol"
```

### Advanced Western Blot

**Multi-Step Protocol**
```bash
1. "Start recording 'Western Blot Quantification'"
2. "Rotate gel -1.5 degrees to level wells"
3. "Apply strong SDS preset for faint bands"
4. "Set Bio-Rad Dual Color ladder in lane 1"
5. "Detect bands in lanes 2-10 with high sensitivity"
6. "Quantify using median background, 5-pixel expansion"
7. "Normalize to housekeeping protein in each lane"
8. "Calculate fold-changes vs control lane"
9. "Export with statistical analysis"
10. "Stop recording and save workflow"
```

### Troubleshooting Difficult Gels

**Noisy/Poor Quality**
```bash
"This gel has lots of background noise, what should I do?"
→ System suggests: bandpass filter + median background + larger expansion

"The bands are very faint, can you enhance them?"  
→ System applies: CLAHE + strong preprocessing + sensitive detection
```

**Geometric Issues**
```bash
"The gel is tilted 3 degrees clockwise"
→ System: "rotate 3 degrees" + continues analysis

"The lanes are curved (smile effect)"
→ System suggests: lane straightening + specialized parameters
```

---

## Troubleshooting

### Common Issues

**LLM Server Problems**
```bash
Issue: "Connection refused to localhost:8080"
Solution: Verify llama-server is running, check port availability

Issue: "Model loading failed"  
Solution: Check model file paths, verify GGUF format compatibility

Issue: "Response timeout"
Solution: Increase timeout, check system resources, verify Metal GPU access
```

**Analysis Problems**
```bash  
Issue: "No bands detected"
Solution: Adjust sensitivity, try preprocessing, check image quality

Issue: "Background estimation failed"
Solution: Use different background region, increase expansion pixels

Issue: "Quantification values too low"
Solution: Check saturation, verify scale calibration, review methodology
```

**Workflow Issues**
```bash
Issue: "Workflow won't save"
Solution: Check disk space, verify directory permissions

Issue: "Workflow playback fails"
Solution: Verify workflow integrity, check action compatibility

Issue: "Can't find saved workflows"
Solution: Check ~/.autodense/workflows/ directory, verify file permissions
```

### Performance Optimization

**Speed Improvements**
```bash
# Use faster background methods
"background_method": "mean"  # vs "median"

# Reduce image size for testing
"Use preview mode for parameter optimization"

# Batch processing
"Run workflow on multiple images simultaneously"
```

**Memory Management**
```bash
# For large images
"Process in sections" or "downsample for preview"

# Clear cache
"Reset context between analyses"
```

### Diagnostic Commands

**System Status**
```bash
"Check system status" → Reports LLM server, memory, disk space
"List available workflows" → Shows workflow library
"Show analysis history" → Displays recent operations
```

**Debug Mode**
```bash
"Enable verbose logging" → Detailed operation logs
"Show last error details" → Error diagnostics
"Test workflow step-by-step" → Manual execution mode
```

---

## Support & Development

### Getting Help

**Documentation**
- This comprehensive guide
- Built-in help system: `"How do I...?"`
- Example workflows included

**Community Resources**  
- GitHub Issues: Bug reports and feature requests
- Laboratory Forums: Share workflows and protocols
- Academic Collaboration: Methodology discussions

### Contributing

**Workflow Sharing**
- Export workflows for community sharing
- Document novel methodologies  
- Contribute standard protocols

**Algorithm Development**
- Propose new analysis methods
- Submit preprocessing improvements
- Validate scientific accuracy

### Roadmap

**Upcoming Features**
- Real-time gel monitoring
- Machine learning band detection
- Cloud workflow sharing
- Regulatory compliance tools
- Extended file format support

**Research Integration**
- Publication-ready figure generation
- Statistical analysis integration
- Laboratory information systems (LIMS)
- Electronic lab notebook (ELN) connectivity

---

*AutoDense Documentation v0.1.0*  
*© 2025 BetterDairy. Built with scientific rigor and AI innovation.*