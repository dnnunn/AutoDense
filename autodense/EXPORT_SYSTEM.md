# User Export System for Labeled Gel Images

## Overview

The AutoDense system provides comprehensive export capabilities for users to save labeled gel images for notebooks, presentations, and publications. All exports include professional markup with lane and band labels while maintaining clear separation from measurement data.

## Export Tools Available

### 🔬 **Core Export Tool**: `export_results`
**Purpose**: Flexible batch export with multiple format options  
**Usage**: Comprehensive export for complete analysis documentation

```json
{
  "image_handle": "img_abc123",
  "export_formats": ["labeled_png", "high_res_png", "presentation_png", "csv", "json"],
  "filename": "my_gel_analysis",
  "output_directory": "/path/to/export/folder",
  "title": "Western Blot Analysis",
  "include_intensities": true,
  "max_width": 1200
}
```

**Returns**:
```json
{
  "exported_files": [
    {
      "format": "labeled_png",
      "path": "/path/to/my_gel_analysis_labeled.png",
      "description": "Labeled gel image for presentations and notebooks"
    },
    {
      "format": "high_res_png", 
      "path": "/path/to/my_gel_analysis_high_res.png",
      "description": "High resolution labeled gel image for publication"
    },
    {
      "format": "presentation_png",
      "path": "/path/to/my_gel_analysis_presentation.png", 
      "description": "Presentation-optimized labeled gel with title and annotations"
    }
  ],
  "usage_guide": {
    "labeled_png": "Perfect for notebooks, presentations, and documentation",
    "high_res_png": "Publication-quality with full resolution and intensity values",
    "presentation_png": "Includes title and summary statistics for slides",
    "csv": "Quantification data for further analysis in Excel/R/Python",
    "json": "Complete analysis metadata and session information"
  }
}
```

### 📓 **Quick Notebook Export**: `export_for_notebook`  
**Purpose**: One-click export optimized for Jupyter notebooks and lab documentation  
**Usage**: Fast export for embedding in research notebooks

```json
{
  "image_handle": "img_abc123",
  "filename": "gel_result.png",
  "output_directory": "~/Downloads",
  "include_intensities": false,
  "max_width": 800
}
```

**Returns**:
```json
{
  "exported_file": "/Users/scientist/Downloads/gel_result.png",
  "notebook_usage": "Perfect for embedding in Jupyter notebooks and lab documentation",
  "markdown_embed": "![Gel Analysis](/Users/scientist/Downloads/gel_result.png)",
  "dimensions": {"width": 800, "height": 600},
  "intensities_included": false
}
```

### 🎥 **Presentation Export**: `export_for_presentation`
**Purpose**: Presentation-ready export with title and summary statistics  
**Usage**: Professional slides for meetings and conferences

```json
{
  "image_handle": "img_abc123", 
  "title": "Protein Expression Analysis",
  "filename": "presentation_slide.png",
  "output_directory": "~/Desktop"
}
```

**Returns**:
```json
{
  "exported_file": "/Users/scientist/Desktop/presentation_slide.png",
  "title": "Protein Expression Analysis",
  "presentation_ready": true,
  "usage": "Ready for PowerPoint, Keynote, or Google Slides",
  "dimensions": {"width": 1000, "height": 750}
}
```

## Export Format Details

### 🖼️ **Labeled PNG Formats**

#### **Standard Labeled PNG** (`labeled_png`)
- **Resolution**: Scaled to max_width (default 1200px)
- **Labels**: Lane numbers (L1, L2...) and band numbers (B1, B2, B3...)
- **Font Size**: 14pt lanes, 10pt bands  
- **Best For**: General documentation, notebooks, reports
- **File Size**: Small (~200-500KB)

#### **High Resolution PNG** (`high_res_png`)  
- **Resolution**: Original image resolution (no scaling)
- **Labels**: All lanes and bands with optional intensity values
- **Font Size**: 18pt lanes, 14pt bands
- **Best For**: Publications, detailed analysis, archival storage
- **File Size**: Large (~2-10MB depending on original)

#### **Presentation PNG** (`presentation_png`)
- **Resolution**: Scaled to max_width (default 1000px) 
- **Labels**: Standard lane/band labels + title + summary statistics
- **Annotations**: "Lanes: 5 | Bands: 23" summary at bottom
- **Font Size**: 16pt title, 14pt lanes, 12pt summary
- **Best For**: Slides, presentations, posters
- **File Size**: Medium (~300-800KB)

### 📊 **Data Export Formats**

#### **CSV Export** (`csv`)
```csv
Lane,Band,Y_Position,Intensity,Area
1,1,45,1250.5,892.3
1,2,123,980.2,756.1
2,1,42,1450.8,945.7
```

#### **JSON Export** (`json`)
```json
{
  "image_info": {
    "handle": "img_abc123",
    "width": 1024, 
    "height": 768,
    "title": "gel_sample.tif"
  },
  "session_summary": "Session: 1 images, 2 overlays, 3 analyses",
  "analysis_timestamp": "2025-01-15T14:30:00Z"
}
```

## Label System

### Lane Labels
- **Format**: `Lane 1`, `Lane 2`, etc.
- **Position**: Top center of each lane
- **Color**: Green text on white background
- **Visibility**: Always visible in all export formats

### Band Labels  
- **Format**: `B1`, `B2`, `B3` (first 3 bands per lane)
- **Extended**: `B1(1250)` when intensities included
- **Position**: Right side of each band
- **Color**: Red text on white background
- **Visibility**: Shown for clarity without cluttering

### Presentation Annotations
- **Title**: User-specified title at bottom of image
- **Summary**: "Lanes: X | Bands: Y" count statistics  
- **Timestamp**: Analysis date/time metadata

## Usage Examples

### 📖 **For Lab Notebooks**
```bash
# Quick notebook export
export_for_notebook({
  "image_handle": "img_abc123",
  "filename": "western_blot_day1.png",
  "include_intensities": false
})

# Embed in Jupyter notebook:
# ![Western Blot Results](western_blot_day1.png)
```

### 🎤 **For Presentations** 
```bash
# Presentation export with custom title
export_for_presentation({
  "image_handle": "img_abc123", 
  "title": "GAPDH Loading Control - Treatment vs Control",
  "filename": "conference_slide_fig2.png"
})
```

### 📄 **For Publications**
```bash
# High-resolution export with intensities
export_results({
  "image_handle": "img_abc123",
  "export_formats": ["high_res_png", "csv"],
  "filename": "figure_3_western_blot",
  "include_intensities": true
})
```

### 📁 **Batch Documentation Export**
```bash
# Complete analysis documentation
export_results({
  "image_handle": "img_abc123",
  "export_formats": ["labeled_png", "presentation_png", "csv", "json"],
  "filename": "experiment_2025_01_15",
  "output_directory": "/Users/scientist/Research/WesternBlots/Experiment_A",
  "title": "Protein Expression Analysis - Sample Set A"
})
```

## Default Export Locations

- **Notebook exports**: `~/Downloads` (easy access)
- **Presentation exports**: `~/Downloads` (immediate use)
- **Batch exports**: User-specified or temp directory
- **Files automatically create parent directories if needed**

## File Naming Conventions

- **Default**: `gel_analysis_[timestamp].png`
- **Notebook**: `gel_labeled_[timestamp].png`  
- **Presentation**: `gel_presentation_[timestamp].png`
- **User-specified**: `[custom_filename]_[format].png`

## Integration with Lab Workflows

### 🔬 **Research Documentation**
1. Analyze gel with AutoDense
2. Export labeled PNG for lab notebook
3. Export CSV data for statistical analysis
4. Archive high-res PNG for future reference

### 📊 **Data Analysis Pipeline**
1. Export CSV for R/Python analysis
2. Export JSON metadata for provenance tracking
3. Export labeled PNG for visualization in analysis tools

### 🎯 **Publication Preparation**
1. Export high-resolution PNG for figure preparation
2. Export CSV for supplementary data tables
3. Use presentation PNG for conference slides

## Quality Assurance

### ✅ **Visual Quality**
- **Anti-aliased text**: Crisp labels at all resolutions
- **Consistent colors**: Green lanes, red bands, clear backgrounds
- **Optimal contrast**: White backgrounds ensure text visibility
- **Professional appearance**: Publication-ready styling

### 🔒 **Data Integrity**
- **Source separation**: Labeled PNGs are visual only
- **Original preservation**: Source image data never modified  
- **Measurement isolation**: All quantification uses original pixels
- **Clear warnings**: Export metadata indicates visual-only usage

### 📏 **Format Optimization**
- **Notebook**: 800px width for optimal notebook display
- **Presentation**: 1000px width for slide projection
- **Publication**: Original resolution for print quality
- **File sizes**: Optimized PNG compression for each use case

This comprehensive export system ensures users can easily create professional, labeled gel images for any research documentation need while maintaining the scientific integrity of their analysis data.