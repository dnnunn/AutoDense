# Phase V Implementation: User-Triggered Natural Language Control

> **Doc Meta**
> - **Purpose:** Ready-to-implement specifications for Phase V natural language interface
> - **Scope:** Complete NL parser, feedback schema, and intent handling system
> - **Owner:** @davidnunn  
> - **Last-verified:** 2025-09-02

## 🎯 Phase V Overview

**Principle**: Scientists guide analysis using natural language and explicit feedback, with Gemini translating lab terminology into bounded parameter adjustments.

**Integration Point**: Builds on Phase IV Vision-Assist by adding user-controlled triggers and natural language parameter specification.

---

## 🔧 Core Implementation Components

### **1. Feedback Schema (user_feedback.yaml)**

Place alongside input images to activate guided re-analysis:

```yaml
user_feedback:
  reason: "under-counted bands"
  targets:
    lanes: 12                 # desired lane count (soft)
    bands_per_lane_min: 4     # soft minimum per lane
  priorities:
    bias: "recall"            # recall | balanced | precision
    preserve_ladder_fit: 0.95 # hard lower bound on R²
  hints:
    ladder_lane_hint: 2
    roi_hint: { x0: 18, y0: 12, w: 780, h: 520 }
  bounds_overrides:           # optional, still intersected with global allowlist
    bands.min_peak_distance_px: [8, 16]
    bands.baseline.window_frac: [0.008, 0.020]
```

**Contract**: Treated as soft priors, raw counts never overwritten

### **2. Natural Language Domain Lexicon**

```yaml
# autodense_autotune/nlp/domain_lexicon.yaml
synonyms:
  assay:
    sds_page: [sds, sds-page, coomassie, denaturing gel, page]
    etbr: [agarose, dna gel, ethidium, etbr, dna]
    colonies: [plates, colony count, blue white, bacterial]
  
  lanes:
    min_peak_distance_frac: [lane spacing, lane separation, lane distance]
    prominence_frac: [lane threshold, lane sensitivity, lane detection]
  
  bands:
    min_peak_distance_px: [band spacing, band separation, band distance]
    baseline.window_frac: [band baseline window, vertical baseline, band background]
    prominence_frac: [band threshold, band sensitivity, band detection]
  
  colonies:
    min_area_px: [min colony size, specks, dust filter, tiny colonies]
    threshold.method: [otsu, phansalkar, local threshold, adaptive]
    colorspace: [lab, rgb, color space]

bias_profiles:
  recall:    
    lanes.prominence_frac: -10%
    bands.min_peak_distance_px: -20%
    bands.baseline.window_frac: -20%
  balanced: {}  # no adjustments
  precision: 
    lanes.prominence_frac: +10%
    bands.min_peak_distance_px: +15%
    bands.baseline.window_frac: +10%

quantifiers:
  slightly: 0.10
  "a bit": 0.10  
  somewhat: 0.20
  more: 0.20
  much: 0.30
  aggressive: 0.35
  very: 0.35
```

### **3. Intent Recognition System**

```python
# autodense_autotune/nlp/intent_parser.py
from typing import Dict, List, Optional, Any
import re
import yaml
from pathlib import Path

class IntentParser:
    def __init__(self, lexicon_path: str):
        with open(lexicon_path, 'r') as f:
            self.lexicon = yaml.safe_load(f)
    
    def parse(self, user_input: str) -> Dict[str, Any]:
        """Parse natural language input into structured intent"""
        intent_result = {
            "intent": self._extract_intent(user_input),
            "assay": self._extract_assay(user_input),
            "patch": self._extract_parameter_patches(user_input),
            "preferences": self._extract_preferences(user_input),
            "constraints": self._extract_constraints(user_input),
            "missing_slots": []
        }
        
        # Validate required slots
        if not intent_result["assay"]:
            intent_result["missing_slots"].append("assay")
            
        return intent_result
    
    def _extract_intent(self, text: str) -> str:
        text_lower = text.lower()
        
        if any(word in text_lower for word in ["optimize", "improve", "better"]):
            return "optimize_params"
        elif any(word in text_lower for word in ["analyze", "run", "process"]):
            if "colon" in text_lower or "plate" in text_lower:
                return "analyze_colonies"
            else:
                return "analyze_gel"
        elif any(word in text_lower for word in ["rerun", "try again", "redo"]):
            return "rerun_with_feedback"
        elif "compare" in text_lower:
            return "compare_runs"
        elif "summary" in text_lower or "report" in text_lower:
            return "summarize_results"
        
        return "analyze_gel"  # default
    
    def _extract_assay(self, text: str) -> Optional[str]:
        text_lower = text.lower()
        
        for assay, synonyms in self.lexicon["synonyms"]["assay"].items():
            if any(syn in text_lower for syn in synonyms):
                return assay
                
        return None
    
    def _extract_parameter_patches(self, text: str) -> Dict[str, Any]:
        patches = {}
        text_lower = text.lower()
        
        # Extract bias profile
        bias = self._extract_bias(text_lower)
        if bias != "balanced":
            bias_patches = self.lexicon["bias_profiles"][bias]
            patches = self._apply_bias_patches(patches, bias_patches)
        
        # Extract quantifier adjustments
        quantifier = self._extract_quantifier(text_lower)
        if quantifier:
            factor = self.lexicon["quantifiers"][quantifier]
            patches = self._apply_quantifier(patches, factor, text_lower)
        
        return patches
    
    def _extract_bias(self, text: str) -> str:
        if any(word in text for word in ["sensitive", "recall", "more", "catch"]):
            return "recall"
        elif any(word in text for word in ["precise", "precision", "strict", "conservative"]):
            return "precision"
        return "balanced"
    
    def _extract_quantifier(self, text: str) -> Optional[str]:
        for quantifier in self.lexicon["quantifiers"]:
            if quantifier in text:
                return quantifier
        return None
```

### **4. Function Calling Schema**

```json
{
  "type": "object",
  "properties": {
    "intent": {
      "type": "string",
      "enum": ["analyze_gel", "analyze_colonies", "optimize_params", "rerun_with_feedback", "compare_runs", "summarize_results"]
    },
    "assay": {
      "type": "string", 
      "enum": ["sds_page", "etbr", "colonies"]
    },
    "input": {
      "type": "object",
      "properties": {
        "path": {"type": "string"},
        "roi": {"type": "array", "items": {"type": "number"}},
        "ladder_lane_hint": {"type": "integer"}
      }
    },
    "preferences": {
      "type": "object",
      "properties": {
        "bias": {"type": "string", "enum": ["recall", "balanced", "precision"]},
        "expected_lanes": {"type": "integer"},
        "bands_per_lane_min": {"type": "integer"}
      }
    },
    "patch": {
      "type": "object",
      "properties": {
        "detect": {
          "type": "object",
          "properties": {
            "lanes": {
              "type": "object",
              "properties": {
                "min_peak_distance_frac": {"type": "number"},
                "prominence_frac": {"type": "number"}
              }
            },
            "bands": {
              "type": "object", 
              "properties": {
                "baseline": {
                  "type": "object",
                  "properties": {
                    "window_frac": {"type": "number"},
                    "quantile": {"type": "number"}
                  }
                },
                "min_peak_distance_px": {"type": "number"}
              }
            }
          }
        },
        "segmentation": {
          "type": "object",
          "properties": {
            "threshold": {
              "type": "object",
              "properties": {
                "method": {"type": "string"},
                "radius": {"type": "number"}
              }
            },
            "colorspace": {"type": "string"},
            "min_area_px": {"type": "number"}
          }
        }
      }
    },
    "constraints": {
      "type": "object",
      "properties": {
        "ladder_r2_min": {"type": "number"},
        "coverage_min": {"type": "number"}
      }
    },
    "budget": {
      "type": "object", 
      "properties": {
        "max_attempts": {"type": "integer"},
        "max_minutes": {"type": "integer"}
      }
    }
  },
  "required": ["intent"]
}
```

### **5. Ready-to-Use Examples**

**Example A**: "Be a bit more sensitive on bands"
```json
{
  "intent": "optimize_params",
  "patch": {
    "detect": {
      "bands": {
        "prominence_frac": "-10%", 
        "min_peak_distance_px": "-10%"
      }
    }
  }
}
```

**Example B**: "This should have ~20 lanes; favor recall but keep ladder fit ≥0.95"
```json
{
  "intent": "optimize_params",
  "assay": "etbr",
  "preferences": {"expected_lanes": 20, "bias": "recall"},
  "patch": {
    "detect": {
      "lanes": {
        "prominence_frac": "-10%",
        "min_peak_distance_frac": "-15%"
      }
    }
  },
  "constraints": {"ladder_r2_min": 0.95},
  "budget": {"max_attempts": 6}
}
```

**Example C**: "Count blue/white colonies; lots of glare"  
```json
{
  "intent": "analyze_colonies",
  "vision_mode": "assist",
  "patch": {
    "segmentation": {
      "threshold": {"method": "Phansalkar", "radius": 25},
      "min_area_px": 40,
      "colorspace": "Lab",
      "blue_cutoff_b": -4.8
    }
  }
}
```

---

## 🚀 Implementation Plan

### **Day 1: Core NL Processing**
1. **Create lexicon system**: `autodense_autotune/nlp/domain_lexicon.yaml`
2. **Implement intent parser**: `autodense_autotune/nlp/intent_parser.py`
3. **Add feedback ingestion**: Extend orchestrator to read `user_feedback.yaml`

### **Day 2: Parameter Patch System**
1. **Implement patch validation**: Against existing allowlist bounds
2. **Add bias profile application**: Recall/precision parameter adjustments  
3. **Integrate quantifier handling**: "slightly", "more", "aggressive" scaling

### **Day 3: Integration & Testing**
1. **Wire to existing orchestrator**: Connect NL parser to Phase IV vision system
2. **Add report fields**: `trigger_source`, `user_feedback_used`, `distance_to_targets`
3. **Create test suite**: 20-30 common lab phrases with gold standard JSON outputs

---

## 🛡️ Safety & Validation

### **Parameter Bounds Enforcement**
All patches validated against existing allowlist from Phase IV:
- `lanes.prominence_frac: [0.02, 0.12]`
- `bands.min_peak_distance_px: [6, 18]` 
- `bands.baseline.window_frac: [0.005, 0.03]`

### **Physics Validation**
Same acceptance criteria as Phase IV:
- Coverage improvement OR stability maintenance
- No ladder R² degradation
- Geometry sanity preserved

### **Fallback Behavior** 
- Missing slots → prompt user once, then use defaults
- Invalid patches → reject with clear error message
- NL parsing failure → fall back to telemetry-only optimization

---

## 📊 Success Criteria

### **NL Parser Accuracy**
- **Target**: >95% accuracy on 30-phrase test suite
- **Metrics**: Correct intent recognition, parameter patch extraction, assay detection

### **User Feedback Integration**
- **Target**: User corrections result in measurable metric improvements
- **Validation**: Before/after coverage, stability, target distance metrics

### **Lab Adoption**
- **Target**: Scientists can express common optimization requests in natural language
- **Examples**: "More sensitive", "favor recall", "should have X lanes", "reduce background"

This implementation is ready for immediate development, building directly on the completed Phase IV foundation.