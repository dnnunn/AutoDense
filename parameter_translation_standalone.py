#!/usr/bin/env python3
"""
Standalone parameter translation functions extracted from Streamlit app for testing.
"""

def translate_ui_to_backend_params(ui_params):
    """
    Safely translate UI parameters to backend-compatible Params dataclass.

    Maps:
    - UI 'gel_type' ("sds_page"/"etbr_agarose") → backend 'modality' ("sds"/"dna")
    - UI 'conf_threshold' (0.0-1.0) → backend 'ladder_min_score' (heuristic mapping)
    - UI 'mw_lane' (int) → ignored (backend uses automatic ladder detection)

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

    # Pass through all other parameters that are recognized by the backend Params dataclass
    known_backend_params = {
        'min_lanes', 'max_lanes', 'comb', 'num_ladders', 'ladder_min_bands',
        'bg_radius', 'invert'
    }

    for key, value in ui_params.items():
        if key in known_backend_params:
            backend_params[key] = value
            translation_log.append(f"✅ {key} {value} → passed through")
        elif key not in ['gel_type', 'conf_threshold', 'mw_lane']:
            translation_log.append(f"⚠️ Unknown parameter '{key}' → ignored")

    return backend_params, translation_log

def validate_and_explain_params(ui_params):
    """
    Validate UI parameters and provide clear explanations of translations.

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

def create_safe_ad_params(ui_params):
    """
    Create ADParams instance safely using parameter translation.

    Note: This is a mock version for testing - in the real app it would create actual ADParams.

    Returns:
        tuple: (ADParams_instance_or_None, explanation_text, error_text)
    """
    try:
        # Validate and translate parameters
        is_valid, backend_params, explanations, errors = validate_and_explain_params(ui_params)

        if not is_valid:
            return None, "", "\n".join(errors)

        # In real app, would create: ad_params = ADParams(**backend_params)
        # For testing, just return the backend_params as a mock
        mock_ad_params = backend_params

        explanation_text = "\n".join(explanations)
        return mock_ad_params, explanation_text, ""

    except Exception as e:
        error_text = f"Failed to create ADParams: {str(e)}"
        return None, "", error_text

if __name__ == "__main__":
    # Simple test when run directly
    print("🧪 Testing parameter translation standalone...")

    # Test the original problematic scenario
    ui_params = {
        "gel_type": "sds_page",
        "conf_threshold": 0.30,
        "mw_lane": 1
    }

    print(f"\nInput UI parameters: {ui_params}")

    is_valid, backend_params, explanations, errors = validate_and_explain_params(ui_params)

    if is_valid:
        print("\n✅ Translation successful!")
        print(f"Backend parameters: {backend_params}")
        print("\nExplanations:")
        for explanation in explanations:
            print(f"  {explanation}")
    else:
        print("\n❌ Validation failed!")
        for error in errors:
            print(f"  Error: {error}")