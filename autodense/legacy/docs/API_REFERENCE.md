# AutoDense API Reference

> **Doc Meta**
> - **Purpose:** Complete API reference for AutoDense tools, data structures, and NL capabilities
> - **Scope:** Tool interfaces, schemas, parameters, and response formats for Gemini AI
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-08-26

## Complete Tool Function Reference for Gemini AI

This document provides the comprehensive API reference for all tool functions available to Gemini AI in AutoDense v12.01 Enhanced. Each tool operates on handles rather than raw image data, maintaining clean separation between AI planning and ImageJ execution.

## Recent Additions (v12.01)

### New Analysis Tools
- `calibrate_standard_curve` - Protein quantification with %CV and LOQ/LLOQ
- `compare_lanes` - MW-aware statistical comparison with multiple testing
- `export_volcano_plot` - Publication-quality volcano plot generation  
- Enhanced `quantify_bands` - PCR normalization support

### Enhanced Colony Analysis
- `start_timeseries_analysis` - Multi-timepoint colony tracking
- `add_timepoint` - Time point addition with alignment
- `align_plate_images` - Standalone plate alignment
- `analyze_xgal_blueness` - X-gal classification and quantification
- `export_timeseries_data` - Comprehensive growth data export

### Frontend Integration
- Voice input framework for hands-free operation
- Document upload integration for experimental metadata
- Console management with menu-driven access

---

## Tool Functions

### start_timeseries_analysis

Initialize time-series colony tracking for growth analysis.

**Parameters:**
```json
{
  "reference_image_handle": "string (required)",
  "max_matching_distance": "number (optional, default: 10.0)",
  "size_change_threshold": "number (optional, default: 2.0)", 
  "track_new_colonies": "boolean (optional, default: true)",
  "measure_morphology": "boolean (optional, default: true)",
  "xgal_analysis": "boolean (optional, default: false)"
}
```

**Parameter Details:**
- `reference_image_handle`: Handle of the first time point image
- `max_matching_distance`: Maximum pixel distance for colony matching between time points
- `size_change_threshold`: Maximum diameter ratio change allowed between time points
- `track_new_colonies`: Whether to track colonies that appear in later time points
- `measure_morphology`: Enable morphology analysis (circularity, texture, etc.)
- `xgal_analysis`: Enable X-gal blueness quantification

**Response:**
```json
{
  "ok": true,
  "tool": "start_timeseries_analysis",
  "data": {
    "tracking_handle": "tracking_1234567890",
    "reference_image_handle": "img_abc123",
    "initial_colony_count": 45,
    "options": {
      "max_matching_distance": 10.0,
      "size_change_threshold": 2.0,
      "track_new_colonies": true,
      "measure_morphology": true,
      "xgal_analysis": false
    }
  }
}
```

**Usage Example:**
```json
{
  "tool": "start_timeseries_analysis",
  "reference_image_handle": "img_t0",
  "measure_morphology": true,
  "xgal_analysis": true
}
```

---

### add_timepoint

Add a new time point to existing time-series analysis with automatic plate alignment.

**Parameters:**
```json
{
  "tracking_handle": "string (required)",
  "image_handle": "string (required)",
  "perform_alignment": "boolean (optional, default: true)",
  "measure_morphology": "boolean (optional, default: true)",
  "xgal_analysis": "boolean (optional, default: false)"
}
```

**Parameter Details:**
- `tracking_handle`: Handle from `start_timeseries_analysis` response
- `image_handle`: Handle of the new time point image
- `perform_alignment`: Whether to align this image to the reference
- `measure_morphology`: Enable morphology analysis for this time point
- `xgal_analysis`: Enable X-gal analysis for this time point

**Response:**
```json
{
  "ok": true,
  "tool": "add_timepoint",
  "data": {
    "tracking_handle": "tracking_1234567890",
    "image_handle": "img_t1",
    "colonies_detected": 47,
    "total_tracks": 45,
    "growing_tracks": 38,
    "alignment_performed": true,
    "timestamp": "2025-08-23T14:30:15"
  }
}
```

**Usage Example:**
```json
{
  "tool": "add_timepoint",
  "tracking_handle": "tracking_1234567890",
  "image_handle": "img_t2", 
  "perform_alignment": true
}
```

---

### align_plate_images

Align a target image to match a reference plate image.

**Parameters:**
```json
{
  "reference_image_handle": "string (required)",
  "target_image_handle": "string (required)",
  "alignment_method": "string (optional, default: 'feature_matching')",
  "tolerance_px": "number (optional, default: 5.0)",
  "allow_rotation": "boolean (optional, default: true)",
  "allow_skewing": "boolean (optional, default: true)"
}
```

**Parameter Details:**
- `reference_image_handle`: Handle of the reference image
- `target_image_handle`: Handle of the image to be aligned
- `alignment_method`: "feature_matching" or "orientation_marks"
- `tolerance_px`: Alignment tolerance in pixels
- `allow_rotation`: Enable rotation correction
- `allow_skewing`: Enable skew correction

**Response:**
```json
{
  "ok": true,
  "tool": "align_plate_images", 
  "data": {
    "aligned_image_handle": "img_aligned_xyz",
    "reference_image_handle": "img_ref",
    "target_image_handle": "img_target",
    "alignment_confidence": 0.87,
    "alignment_method": "feature_matching",
    "key_points_found": 23,
    "metadata": {
      "features_ref": 25,
      "features_target": 28,
      "matches_total": 23,
      "matches_inliers": 18,
      "rmse_pixels": 2.3
    },
    "parameters_used": {
      "method": "feature_matching",
      "tolerance_px": 5.0,
      "allow_rotation": true,
      "allow_skewing": true
    }
  }
}
```

**Usage Example:**
```json
{
  "tool": "align_plate_images",
  "reference_image_handle": "img_ref",
  "target_image_handle": "img_target",
  "alignment_method": "orientation_marks"
}
```

---

### analyze_xgal_blueness

Analyze X-gal blueness for colonies in an image.

**Parameters:**
```json
{
  "image_handle": "string (required)",
  "use_rgb_analysis": "boolean (optional, default: true)",
  "analysis_radius": "number (optional, default: 8)",
  "normalize_lighting": "boolean (optional, default: true)"
}
```

**Parameter Details:**
- `image_handle`: Handle of the image containing colonies
- `use_rgb_analysis`: Use RGB color space analysis (vs HSV)
- `analysis_radius`: Radius in pixels for colony color analysis
- `normalize_lighting`: Correct for lighting variations

**Response:**
```json
{
  "ok": true,
  "tool": "analyze_xgal_blueness",
  "data": {
    "image_handle": "img_plate",
    "colonies": {
      "colony_0": {
        "blueness_score": 0.23,
        "blue_intensity": 0.31,
        "color_purity": 0.45,
        "spatial_uniformity": 0.82,
        "classification": "light_blue",
        "confidence": 0.89,
        "color_metrics": {
          "rgb_averages": {"red": 145, "green": 160, "blue": 201},
          "hsv_averages": {"hue": 215, "saturation": 0.28, "value": 0.79}
        }
      }
    },
    "summary": {
      "total_colonies": 45,
      "white_colonies": 23,
      "light_blue_colonies": 15,
      "medium_blue_colonies": 6,
      "deep_blue_colonies": 1,
      "transformation_efficiency": 0.49
    }
  }
}
```

**Classification Scale:**
- `white`: blueness_score < 0.1
- `light_blue`: 0.1 ≤ blueness_score < 0.3
- `medium_blue`: 0.3 ≤ blueness_score < 0.6  
- `deep_blue`: blueness_score ≥ 0.6

**Usage Example:**
```json
{
  "tool": "analyze_xgal_blueness",
  "image_handle": "img_xgal_plate",
  "use_rgb_analysis": true,
  "analysis_radius": 10
}
```

---

### export_timeseries_data

Export time-series colony growth analysis data.

**Parameters:**
```json
{
  "tracking_handle": "string (required)",
  "export_formats": "array (optional)",
  "filename": "string (optional, default: 'timeseries_analysis')"
}
```

**Parameter Details:**
- `tracking_handle`: Handle from time-series analysis
- `export_formats`: Array of format strings: ["json", "csv"]
- `filename`: Base filename for exports (without extension)

**Response:**
```json
{
  "ok": true,
  "tool": "export_timeseries_data",
  "data": {
    "tracking_handle": "tracking_1234567890",
    "export_data": {
      "time_points": [
        {"timestamp": "2025-08-23T14:00:00", "colony_count": 45},
        {"timestamp": "2025-08-23T16:00:00", "colony_count": 47}
      ],
      "tracks": [
        {
          "track_id": "track_1",
          "statistics": {
            "total_area_growth_mm2": 0.786,
            "total_diameter_growth_mm": 0.27,
            "max_blueness": 0.35,
            "time_points_count": 2
          }
        }
      ],
      "summary": {
        "total_tracks": 45,
        "total_time_points": 2,
        "growing_tracks": 38,
        "avg_final_area_mm2": 2.87
      }
    },
    "exported_files": [
      {
        "format": "json",
        "path": "/tmp/timeseries_analysis.json",
        "description": "Complete time-series analysis data"
      },
      {
        "format": "csv", 
        "path": "/tmp/timeseries_analysis.csv",
        "description": "Colony growth data in spreadsheet format"
      }
    ],
    "summary": {
      "total_tracks": 45,
      "growing_tracks": 38,
      "avg_final_area_mm2": 2.87
    }
  }
}
```

**Usage Example:**
```json
{
  "tool": "export_timeseries_data",
  "tracking_handle": "tracking_1234567890",
  "export_formats": ["csv", "json"],
  "filename": "growth_experiment_24h"
}
```

---

## Data Structures

### Colony Object
```json
{
  "id": "colony_50_60",
  "center": {"x": 50.0, "y": 60.0},
  "area": 2.341,
  "diameter": 1.72,
  "morphology": {
    "circularity": 0.89,
    "solidity": 0.94,
    "aspect_ratio": 1.12,
    "texture_variance": 15.3,
    "edge_type": "smooth"
  },
  "blueness": 0.23,
  "confidence": 0.87
}
```

### Colony Track Object
```json
{
  "track_id": "track_1",
  "time_points": [
    // Array of Colony objects at each time point
  ],
  "statistics": {
    "total_area_growth_mm2": 0.786,
    "total_diameter_growth_mm": 0.27,
    "max_area_mm2": 3.127,
    "avg_circularity": 0.88,
    "time_points_count": 3,
    "max_blueness": 0.35,
    "final_blueness": 0.31
  }
}
```

### Alignment Result Object
```json
{
  "confidence": 0.87,
  "method": "feature_matching",
  "key_points": [
    {"x": 123.4, "y": 567.8},
    {"x": 234.5, "y": 678.9}
  ],
  "metadata": {
    "features_ref": 25,
    "features_target": 28,
    "matches_total": 23,
    "rmse_pixels": 2.3
  }
}
```

---

## Error Handling

### Common Error Codes

**tracking_not_found**
```json
{
  "ok": false,
  "error": {
    "code": "tracking_not_found",
    "message": "Time-series tracking not found",
    "param": "tracking_handle"
  }
}
```

**alignment_failed**
```json
{
  "ok": false,
  "error": {
    "code": "alignment_failed", 
    "message": "Could not reliably align images",
    "param": "alignment_confidence"
  }
}
```

**no_colonies_found**
```json
{
  "ok": false,
  "error": {
    "code": "no_colonies_found",
    "message": "No colonies found for blueness analysis", 
    "param": "colony_detection"
  }
}
```

### Error Recovery
- Check image handles are valid and images are loaded
- Verify tracking handles match active time-series analyses
- Ensure images have sufficient colonies for analysis
- Use lower alignment thresholds for difficult alignments
- Check image quality and lighting for X-gal analysis

---

## Usage Patterns

### Complete Time-Series Workflow
```json
// 1. Initialize tracking
{
  "tool": "start_timeseries_analysis",
  "reference_image_handle": "img_t0",
  "measure_morphology": true,
  "xgal_analysis": true
}

// 2. Add subsequent time points
{
  "tool": "add_timepoint",
  "tracking_handle": "tracking_123",
  "image_handle": "img_t1"
}

{
  "tool": "add_timepoint", 
  "tracking_handle": "tracking_123",
  "image_handle": "img_t2"
}

// 3. Export results
{
  "tool": "export_timeseries_data",
  "tracking_handle": "tracking_123",
  "export_formats": ["csv", "json"]
}
```

### Standalone X-gal Analysis
```json
{
  "tool": "analyze_xgal_blueness",
  "image_handle": "img_transformation_plate",
  "use_rgb_analysis": true,
  "normalize_lighting": true
}
```

### Manual Plate Alignment
```json
{
  "tool": "align_plate_images",
  "reference_image_handle": "img_ref",
  "target_image_handle": "img_rotated",
  "alignment_method": "orientation_marks",
  "allow_rotation": true
}
```

---

## Performance Considerations

- **Memory Usage**: Each time point stores full ImagePlus objects
- **Processing Time**: Scales with colony count and image size
- **Alignment Speed**: Feature matching faster than orientation marks for many colonies
- **Export Size**: JSON exports can be large for long time series
- **Tracking Accuracy**: Higher with more distinctive colony patterns

## Integration Notes

- All tools follow handle-based architecture
- Compatible with existing SessionStore and logging systems  
- Supports recovery mechanisms for failed operations
- Integrates with workflow preset system
- Compatible with existing export and visualization tools