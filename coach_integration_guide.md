# AutoDense Coach Integration Guide

This document provides step-by-step instructions for integrating the AutoDense Coach into your existing workflow.

## 📁 File Structure

```
AutoDense/
├── autodense/
│   └── coach_rules.py          # Stateless rule engine
├── ui/
│   └── CoachBanner.tsx         # React component
├── backend/
│   └── app.py                  # FastAPI endpoints
├── pyproject.toml              # Python project config
├── requirements.txt            # Dependencies
└── runs/                       # Runtime data directory
    └── <run_id>/
        ├── metrics.json        # Analysis metrics
        └── params.json         # Parameters (optional)
```

## 🔌 Integration Workflow

### 1. After Each Analysis Step Completes

```python
# In your analysis pipeline
def complete_analysis_step(step_name: str, run_id: str, results: Any):
    # Extract metrics from your analysis results
    metrics = {
        "img": {
            "w": image.width,
            "h": image.height, 
            "dtype": str(image.dtype),
            "sat_pct": calculate_saturation_percentage(image),
            "blur_sigma": estimate_blur(image),
            "illum_gradient": calculate_illumination_gradient(image)
        },
        "sds": {  # For SDS-PAGE analysis
            "lanes": detected_lanes.count,
            "lane_width_px": average_lane_width,
            "lanewise_bg": params.get('lanewise_background', False),
            "bands_total": total_bands_detected,
            "ladder_r2": ladder_fit_quality.r_squared,
            "residuals_monotonic": check_residuals_monotonic(ladder_fit),
            "min_prom": params.get('min_band_prominence', 0.02),
            "flat_peak_score": calculate_peak_flatness(bands)
        },
        "etbr": {  # For EtBr gel analysis
            "discrete_peaks": count_discrete_peaks(lanes),
            "smear_score": calculate_smear_score(intensity_profiles)
        },
        "colony": {  # For colony analysis
            "count": colony_results.total_count,
            "touching_rate": colony_results.touching_percentage,
            "adaptive_threshold": params.get('threshold_method') == 'adaptive',
            "flatfield_applied": params.get('flatfield_correction', False),
            "avg_radius_px": colony_results.average_radius
        },
        "run": {
            "id": run_id,
            "step_id": step_name
        }
    }
    
    # Save metrics to file system
    run_dir = Path("runs") / run_id
    run_dir.mkdir(parents=True, exist_ok=True)
    
    (run_dir / "metrics.json").write_text(json.dumps(metrics, indent=2))
    (run_dir / "params.json").write_text(json.dumps(params, indent=2))
    
    # Trigger UI to fetch new hints
    notify_frontend_to_refresh_hints(run_id)
```

### 2. Frontend Integration

```tsx
// In your main analysis component
import { CoachBanner } from './ui/CoachBanner';

function AnalysisPage({ runId }: { runId: string }) {
  const [hints, setHints] = useState([]);
  const [loading, setLoading] = useState(false);

  // Fetch hints after analysis steps
  const fetchHints = async () => {
    try {
      const response = await fetch(`/api/coach/hints?run_id=${runId}`);
      const data = await response.json();
      setHints(data.hints);
    } catch (error) {
      console.error('Failed to fetch hints:', error);
    }
  };

  // Handle hint actions
  const handleAction = async (action: HintAction) => {
    if (action.op === 'rerun_step') {
      setLoading(true);
      try {
        const response = await fetch('/api/coach/rerun', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            run_id: runId,
            step_id: action.step_id,
            params_delta: action.params_delta
          })
        });
        
        const result = await response.json();
        
        // Update UI with new run results
        setRunId(result.run_id);  // Switch to cloned run
        setHints(result.hints);   // Show updated hints
        
        // Reload analysis results
        await refreshAnalysisResults(result.run_id);
        
      } catch (error) {
        console.error('Failed to rerun step:', error);
      } finally {
        setLoading(false);
      }
    } else if (action.op === 'preview_overlay_diff') {
      // Show overlay comparison modal
      showOverlayPreview(action.step_id, action.params_delta);
    } else if (action.op === 'open_docs') {
      // Open documentation
      openDocumentation(action.docs_id);
    }
  };

  // Handle hint dismissal
  const handleDismiss = (hintId: string) => {
    // Store dismissal in session storage
    const dismissed = JSON.parse(sessionStorage.getItem('dismissedHints') || '[]');
    dismissed.push(hintId);
    sessionStorage.setItem('dismissedHints', JSON.stringify(dismissed));
    
    // Filter out dismissed hint
    setHints(hints.filter(h => h.id !== hintId));
  };

  // Fetch hints when component mounts or runId changes
  useEffect(() => {
    if (runId) {
      fetchHints();
    }
  }, [runId]);

  return (
    <div>
      <CoachBanner 
        hints={hints}
        onAction={handleAction}
        onDismiss={handleDismiss}
      />
      
      {loading && (
        <div>Applying coach suggestions and re-running analysis...</div>
      )}
      
      {/* Your existing analysis UI */}
      <AnalysisResults runId={runId} />
    </div>
  );
}
```

### 3. Backend Pipeline Integration

```python
# In your analysis pipeline
class AnalysisPipeline:
    def rerun_step(self, step_id: str, run_dir: Path, params: Dict[str, Any]):
        """Called by /api/coach/rerun endpoint"""
        
        if step_id == "sds.quantify_lanes":
            return self.run_sds_quantification(run_dir, params)
        elif step_id == "sds.fit_ladder":
            return self.run_ladder_fitting(run_dir, params)
        elif step_id == "etbr.quantify_lane_profiles":
            return self.run_etbr_quantification(run_dir, params)
        elif step_id == "colony.segment":
            return self.run_colony_segmentation(run_dir, params)
        else:
            raise ValueError(f"Unknown step_id: {step_id}")
    
    def run_sds_quantification(self, run_dir: Path, params: Dict[str, Any]):
        # Load original image
        image_path = run_dir / "input_image.tiff"
        image = load_image(image_path)
        
        # Apply coach-suggested parameters
        if params.get('exposure_comp'):
            image = apply_exposure_compensation(image, params['exposure_comp'])
        
        if params.get('lanewise_background'):
            background_radius = params.get('rolling_ball_radius', 50)
            image = apply_lanewise_background_subtraction(image, background_radius)
        
        # Re-run quantification with updated parameters
        lanes = detect_lanes(image)
        bands = detect_bands(lanes, min_prominence=params.get('min_band_prominence', 0.02))
        
        # Save updated results
        results = {
            "lanes": lanes,
            "bands": bands,
            "overlay_image": generate_overlay_image(image, lanes, bands)
        }
        
        save_results(run_dir / "results.json", results)
        save_overlay(run_dir / "overlay.png", results["overlay_image"])
        
        return results
```

## 🔧 API Endpoints

### GET /api/coach/hints
Fetch hints for a specific run.

**Query Parameters:**
- `run_id`: The analysis run identifier

**Response:**
```json
{
  "run_id": "RUN_001",
  "hints": [
    {
      "id": "HINT_SDS_SATURATION",
      "severity": "warn",
      "confidence": 0.847,
      "applies_to": "sds",
      "symptoms": ["peak_clipped_high"],
      "why_it_matters": "Clipped band cores break intensity proportionality...",
      "suggested_changes": {
        "exposure_comp": -0.3,
        "min_band_prominence": 0.016
      },
      "actions": [
        {
          "label": "Apply & re-run lane quant",
          "op": "rerun_step",
          "step_id": "sds.quantify_lanes",
          "params_delta": {"exposure_comp": -0.3}
        }
      ],
      "docs": "tip_sds_saturation",
      "dismissible_for_session": true
    }
  ]
}
```

### POST /api/coach/rerun
Apply parameter changes and re-run analysis step.

**Request Body:**
```json
{
  "run_id": "RUN_001",
  "step_id": "sds.quantify_lanes", 
  "params_delta": {
    "exposure_comp": -0.3,
    "min_band_prominence": 0.016
  }
}
```

**Response:**
```json
{
  "run_id": "RUN_001_coach",
  "applied": {
    "exposure_comp": -0.3,
    "min_band_prominence": 0.016
  },
  "hints": [
    // Updated hints after re-running with new parameters
  ]
}
```

## 🧪 Testing the Integration

### 1. Start the Backend
```bash
cd AutoDense
python -m uvicorn backend.app:app --reload --host 0.0.0.0 --port 8000
```

### 2. Test with cURL
```bash
# Create test data
mkdir -p runs/TEST_001
echo '{"img":{"sat_pct":0.05},"sds":{"ladder_r2":0.96}}' > runs/TEST_001/metrics.json

# Get hints
curl 'http://localhost:8000/api/coach/hints?run_id=TEST_001' | jq

# Apply a fix
curl -X POST 'http://localhost:8000/api/coach/rerun' \
  -H 'Content-Type: application/json' \
  -d '{"run_id":"TEST_001","step_id":"sds.quantify_lanes","params_delta":{"exposure_comp":-0.3}}' | jq
```

### 3. Frontend Testing
```tsx
// Test component
import { CoachBanner } from './ui/CoachBanner';

const testHints = [
  {
    id: "HINT_SDS_SATURATION",
    severity: "warn" as const,
    confidence: 0.847,
    applies_to: "sds" as const,
    symptoms: ["peak_clipped_high"],
    why_it_matters: "Clipped band cores break intensity proportionality and inflate wide bands.",
    suggested_changes: {"exposure_comp": -0.3},
    actions: [
      {
        label: "Apply & re-run lane quant",
        op: "rerun_step" as const,
        step_id: "sds.quantify_lanes",
        params_delta: {"exposure_comp": -0.3}
      }
    ],
    dismissible_for_session: true
  }
];

function TestCoachBanner() {
  return (
    <CoachBanner 
      hints={testHints}
      onAction={(action) => console.log('Action:', action)}
      onDismiss={(id) => console.log('Dismissed:', id)}
    />
  );
}
```

## 🚨 Important Notes

### Cloning Strategy
- Always clone runs when applying coach suggestions to preserve original user data
- Use descriptive clone naming: `{original_run_id}_coach_{timestamp}`
- Provide UI to switch between original and coach-modified runs

### Error Handling
```python
# Robust error handling in rules
def evaluate_hints(metrics: Dict[str, Any], params: Dict[str, Any], *, max_hints: int = 2) -> List[Dict[str, Any]]:
    hints: List[Hint] = []
    for rule in REGISTRY:
        try:
            h = rule(metrics, params)
        except Exception as e:
            logger.warning(f"Rule {rule.__name__} failed: {e}")
            h = None  # Continue with other rules
        if h is not None and (h.confidence >= 0.55 or h.severity == "error"):
            hints.append(h)
    # ... rest of function
```

### Performance Considerations
- Cache hints for recently completed runs
- Debounce hint requests during rapid analysis updates
- Use background workers for expensive metric calculations

## 🎯 Success Metrics

Monitor these metrics to validate coach effectiveness:

1. **Hint Acceptance Rate**: % of suggestions users actually apply
2. **Quality Improvement**: Measurable improvements in analysis metrics after applying hints
3. **User Engagement**: Frequency of coach usage across different analysis types
4. **Error Reduction**: Decrease in analysis failures or poor-quality results

The coach should improve scientific measurement quality while maintaining user autonomy and workflow efficiency.