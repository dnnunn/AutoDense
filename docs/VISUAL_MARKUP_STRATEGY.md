# Visual Markup Strategy for Gel Analysis

## Overview

The AutoDense system implements a **dual-data approach** for gel analysis:
1. **Original Image Data**: Used exclusively for all measurements and quantification
2. **Labeled Visual References**: PNG exports with overlays for user communication and Gemini visual understanding

## Core Principle: Separation of Measurement and Communication

### 🔬 **Measurement Tools** (Original Data Only)
- `detect_lanes` - Lane detection on original pixels
- `detect_bands` - Band detection on original pixels  
- `quantify_bands` - Intensity measurements on original pixels
- All analysis operations use `ImagePlus` original pixel values

### 🎨 **Visual Communication Tools** (Labeled PNGs)
- `render_overlay_png` - Current view with basic labels
- `create_labeled_reference` - Comprehensive labeled reference image
- These create PNGs with burned-in overlays for visual communication only

## Labeling System

### Lane Labels
- **Format**: `Lane 1`, `Lane 2`, `L1`, `L2`, etc.
- **Position**: Top of image, centered over each lane
- **Color**: Green text on white background
- **Font**: Arial Bold, 14-16pt

### Band Labels  
- **Format**: `B1`, `B2`, `B3` (first 3 bands per lane)
- **Extended**: `L1B1`, `L2B3` for specific lane-band references
- **Position**: Right side of each band
- **Color**: Red text on white background  
- **Font**: Arial Plain, 10-12pt

### Intensity Labels (Optional)
- **Format**: `B1(1250)` - band number with intensity value
- **Usage**: When `include_intensities=true` in labeled reference
- **Purpose**: Quick visual reference of quantification results

## Tool Usage Patterns

### 1. **Standard Analysis Workflow**
```java
// 1. Detect lanes on original data
detect_lanes({"image_handle": "img_abc123"})

// 2. Detect bands on original data  
detect_bands({"image_handle": "img_abc123"})

// 3. Create labeled reference for user communication
create_labeled_reference({"image_handle": "img_abc123"}) 
→ Returns PNG path for Gemini to view and discuss with user

// 4. Quantify using original data only
quantify_bands({"image_handle": "img_abc123", "analysis_handle": "analysis_xyz"})
```

### 2. **User Communication Workflow**
```java
// User asks: "What do you see in lane 2?"

// 1. Create labeled reference  
create_labeled_reference({"image_handle": "img_abc123"})

// 2. Gemini views PNG and can reference:
// "In Lane 2, I can see 4 bands labeled B1-B4. Band B2 appears most intense..."

// 3. User can now reference specific features:
// "Please quantify band B3 in Lane 2"
```

### 3. **Measurement Validation Workflow**
```java
// All measurements reference original data:
quantify_bands() → "measurement_source": "original_image_data"

// Visual references include clear warnings:
render_overlay_png() → "usage_note": "This PNG shows labeled lanes/bands for visual reference only. Use original image data for all measurements."
```

## Implementation Details

### Overlay Creation
```java
// Lane overlay with label
Roi laneRoi = new Roi(x, 0, width, height);
laneRoi.setStrokeColor(new Color(0, 255, 0, 180)); // Green
TextRoi laneLabel = new TextRoi(x + width/2 - 10, 15, "L" + (i + 1));
laneLabel.setStrokeColor(new Color(0, 255, 0));
laneLabel.setFillColor(new Color(255, 255, 255, 200)); // White background
overlay.add(laneRoi);
overlay.add(laneLabel);
```

### PNG Export Process
```java
// 1. Apply overlay to ImageJ display
img.image.setOverlay(overlay);
img.image.updateAndDraw();

// 2. Create duplicate for PNG export
ImagePlus labeledImage = img.image.duplicate();
labeledImage.setOverlay(overlay);
labeledImage = labeledImage.flatten(); // Burn overlay into pixels

// 3. Save as PNG for visual communication
FileSaver fs = new FileSaver(labeledImage);
fs.saveAsPng(outputPath.toString());
```

## Data Integrity Safeguards

### 1. **Clear Method Documentation**
```java
/**
 * Tool: quantify_bands
 * Quantify band intensities using ORIGINAL image data only.
 * CRITICAL: This method operates on the original ImagePlus data, not any PNG exports.
 * All measurements are performed on pixel values from the source image.
 */
```

### 2. **Response Metadata**
```json
{
  "quantification_handle": "analysis_abc123",
  "results": [...],
  "measurement_source": "original_image_data",
  "data_integrity": "All measurements performed on original ImagePlus pixel values"
}
```

### 3. **PNG Usage Warnings**
```json
{
  "reference_png": "/tmp/labeled_reference_123.png",
  "reference_purpose": "Visual communication tool with comprehensive labels",
  "measurement_warning": "CRITICAL: Use original image data for all measurements, not this labeled PNG"
}
```

## Gemini Integration Guidelines

### Visual References for Communication
```
When user asks about specific gel features:
1. Use create_labeled_reference() to generate labeled PNG
2. View the PNG to understand lane/band layout
3. Reference specific features: "Lane 3, Band 2" or "L3B2"
4. Guide user using visual labels for clear communication
```

### Measurement Operations
```
For all quantitative analysis:
1. NEVER use PNG files for measurements
2. Always use original image_handle with quantify_bands()
3. Ensure measurement tools receive original ImagePlus data
4. Verify responses include "measurement_source": "original_image_data"
```

### User Discussion Examples

**Good Practice**:
```
User: "The third lane looks interesting"
Gemini: Let me create a labeled reference so we can discuss specific bands.
[calls create_labeled_reference()]
Gemini: I can see Lane 3 has 5 bands (B1-B5). Band B3 appears particularly intense. Would you like me to quantify this specific band?
```

**Measurement Clarity**:
```
User: "What's the intensity of the second band in lane 4?"
Gemini: I'll quantify Lane 4, Band 2 using the original image data.
[calls quantify_bands() with original image_handle]
Gemini: Lane 4, Band 2 has an intensity of 1,847 units (measured from original pixel values).
```

## Benefits of This Approach

### 🎯 **Accuracy**
- All measurements use pristine original pixel data
- No compression artifacts or overlay interference
- Maintains scientific integrity of quantification

### 💬 **Communication**
- Clear visual references with consistent labeling
- Users can reference specific lanes/bands unambiguously  
- Gemini can discuss features using visual context

### 🛡️ **Data Integrity**
- Strict separation between measurement and visualization
- Clear warnings about PNG usage limitations
- Metadata tracking of measurement sources

### 🔄 **Workflow Efficiency**
- Visual references speed up user communication
- Labeled features reduce ambiguity in discussions
- Systematic approach to analysis and reporting

This dual-data strategy ensures both scientific accuracy and effective user communication in gel analysis workflows.