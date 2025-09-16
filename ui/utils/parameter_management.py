"""Parameter management utilities for AutoDense.

This module contains parameter translation and validation functions with no Streamlit dependencies.
These functions handle mapping between UI parameters and backend analysis parameters.
"""

from typing import Dict, Any, Tuple, List, Optional


def _get_ad_params_class():
    """Lazy import of ADParams with proper error handling.

    Attempts to import from multiple possible locations to handle different
    installation scenarios. Returns None if ADParams is not available.

    Returns:
        ADParams class or None if import fails
    """
    try:
        from scripts.autodense.orchestrator.pipeline import Params
        return Params
    except ImportError:
        try:
            from autodense.orchestrator.pipeline import Params
            return Params
        except ImportError:
            return None


def translate_ui_to_backend_params(ui_params: Dict[str, Any]) -> Tuple[Dict[str, Any], List[str]]:
    """
    Safely translate UI parameters to backend-compatible Params dataclass.

    Maps:
    - UI 'gel_type' ("sds_page"/"etbr_agarose") → backend 'modality' ("sds"/"dna")
    - UI 'conf_threshold' (0.0-1.0) → backend 'ladder_min_score' (heuristic mapping)
    - UI 'mw_lane' (int) → ignored (backend uses automatic ladder detection)

    Args:
        ui_params: Dictionary of UI parameters from Streamlit widgets

    Returns:
        tuple: (backend_params_dict, translation_log)
    """
    translation_log = []
    backend_params = {}

    # Handle gel_type → modality mapping
    if 'gel_type' in ui_params:
        gel_type = ui_params['gel_type']
        if gel_type == 'sds_page':
            backend_params['modality'] = 'sds'
            translation_log.append("✅ gel_type 'sds_page' → modality 'sds'")
        elif gel_type == 'etbr_agarose':
            backend_params['modality'] = 'dna'
            translation_log.append("✅ gel_type 'etbr_agarose' → modality 'dna'")
        else:
            # Default to SDS for unknown types
            backend_params['modality'] = 'sds'
            translation_log.append(f"⚠️ Unknown gel_type '{gel_type}' → defaulting to modality 'sds'")

    # Handle conf_threshold → ladder_min_score mapping
    if 'conf_threshold' in ui_params:
        conf_threshold = float(ui_params['conf_threshold'])
        # Heuristic mapping: conf_threshold tends to be higher than ladder_min_score
        # Backend ladder_min_score typically ranges 0.25-0.5, UI conf_threshold 0.2-0.9
        if conf_threshold >= 0.8:
            ladder_min_score = 0.45
        elif conf_threshold >= 0.6:
            ladder_min_score = 0.40
        elif conf_threshold >= 0.4:
            ladder_min_score = 0.35
        elif conf_threshold >= 0.2:
            ladder_min_score = 0.30
        else:
            ladder_min_score = 0.25

        backend_params['ladder_min_score'] = ladder_min_score
        translation_log.append(f"✅ conf_threshold {conf_threshold:.2f} → ladder_min_score {ladder_min_score:.2f}")

    # Handle mw_lane → ignore with explanation
    if 'mw_lane' in ui_params:
        mw_lane = ui_params['mw_lane']
        translation_log.append(f"ℹ️ mw_lane {mw_lane} → ignored (backend uses automatic ladder detection)")

    # Handle manual lane boundaries
    if 'manual_lane_boundaries' in ui_params:
        lane_boundaries = ui_params['manual_lane_boundaries']
        try:
            if lane_boundaries and len(lane_boundaries) > 0:
                # Convert SimpleLaneBoundary objects to (x_start, x_end) tuples if needed
                if hasattr(lane_boundaries[0], 'left_px') and hasattr(lane_boundaries[0], 'right_px'):
                    # Convert from SimpleLaneBoundary format
                    backend_params['manual_lane_boundaries'] = [
                        (int(boundary.left_px), int(boundary.right_px))
                        for boundary in lane_boundaries
                    ]
                    translation_log.append(f"✅ manual_lane_boundaries → {len(lane_boundaries)} manual lanes specified (SimpleLaneBoundary format)")
                elif isinstance(lane_boundaries[0], (list, tuple)) and len(lane_boundaries[0]) >= 2:
                    # Assume already in tuple format
                    backend_params['manual_lane_boundaries'] = [
                        (int(boundary[0]), int(boundary[1])) for boundary in lane_boundaries
                    ]
                    translation_log.append(f"✅ manual_lane_boundaries → {len(lane_boundaries)} lanes (tuple format)")
                else:
                    # Invalid format, skip manual boundaries
                    translation_log.append(f"⚠️ manual_lane_boundaries → invalid format {type(lane_boundaries[0])}, using automatic detection")
            else:
                translation_log.append("ℹ️ manual_lane_boundaries → None (using automatic detection)")
        except Exception as e:
            translation_log.append(f"⚠️ manual_lane_boundaries → conversion error: {str(e)}, using automatic detection")

    # Pass through all other parameters that are recognized by the backend Params dataclass
    known_backend_params = {
        'min_lanes', 'max_lanes', 'comb', 'num_ladders', 'ladder_min_bands',
        'bg_radius', 'invert', 'manual_lane_boundaries'
    }

    for key, value in ui_params.items():
        if key in known_backend_params:
            backend_params[key] = value
            translation_log.append(f"✅ {key} {value} → passed through")
        elif key not in ['gel_type', 'conf_threshold', 'mw_lane', 'manual_lane_boundaries']:
            translation_log.append(f"⚠️ Unknown parameter '{key}' → ignored")

    return backend_params, translation_log


def validate_and_explain_params(ui_params: Dict[str, Any]) -> Tuple[bool, Dict[str, Any], List[str], List[str]]:
    """
    Validate UI parameters and provide clear explanations of translations.

    Args:
        ui_params: Dictionary of UI parameters from Streamlit widgets

    Returns:
        tuple: (is_valid, backend_params_dict, explanation_lines, error_messages)
    """
    errors = []
    explanations = []

    # Validate conf_threshold range
    if 'conf_threshold' in ui_params:
        conf_threshold = ui_params['conf_threshold']
        if not (0.0 <= conf_threshold <= 1.0):
            errors.append(f"conf_threshold must be between 0.0 and 1.0, got {conf_threshold}")

    # Validate mw_lane if provided
    if 'mw_lane' in ui_params:
        mw_lane = ui_params['mw_lane']
        if not isinstance(mw_lane, int) or mw_lane < 1:
            errors.append(f"mw_lane must be a positive integer, got {mw_lane}")

    # Validate gel_type
    if 'gel_type' in ui_params:
        gel_type = ui_params['gel_type']
        if gel_type not in ['sds_page', 'etbr_agarose']:
            errors.append(f"gel_type must be 'sds_page' or 'etbr_agarose', got '{gel_type}'")

    if errors:
        return False, {}, [], errors

    # Perform translation
    backend_params, translation_log = translate_ui_to_backend_params(ui_params)

    # Add explanation header
    explanations.append("**Parameter Translation:**")
    explanations.extend(translation_log)

    # Add backend parameter summary
    explanations.append("\n**Final Backend Parameters:**")
    for key, value in backend_params.items():
        explanations.append(f"• {key}: {value}")

    return True, backend_params, explanations, []


def create_safe_ad_params(ui_params: Dict[str, Any], ad_params_class=None) -> Tuple[Optional[Any], str, str]:
    """
    Create ADParams instance safely using parameter translation.

    Args:
        ui_params: Dictionary of UI parameters from Streamlit widgets
        ad_params_class: ADParams class for dependency injection (optional)

    Returns:
        tuple: (ADParams_instance_or_None, explanation_text, error_text)
    """
    if ad_params_class is None:
        ad_params_class = _get_ad_params_class()

    if ad_params_class is None:
        return None, "", "ADParams not available (pipeline not imported)"

    try:
        # Validate and translate parameters
        is_valid, backend_params, explanations, errors = validate_and_explain_params(ui_params)

        if not is_valid:
            return None, "", "\n".join(errors)

        # Create ADParams instance with translated parameters
        ad_params = ad_params_class(**backend_params)

        explanation_text = "\n".join(explanations)
        return ad_params, explanation_text, ""

    except Exception as e:
        error_text = f"Failed to create ADParams: {str(e)}"
        return None, "", error_text