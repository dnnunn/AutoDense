# autodense/assist/nlp_intent.py
"""
Heuristic translator from a plain-English prompt to a safe, typed recipe (uses the same ops as your Manipulate builder).
"""
import re
from typing import Optional, Dict, Any, List

def parse_prompt_to_recipe(prompt: str, samples: List[str]) -> Optional[Dict[str, Any]]:
    """
    Examples:
      - "compare WT vs MutA in 45–60 kDa"
      - "fold change MutB over WT between 800 and 1500 bp"
    Returns a recipe dict or None.
    """
    p = prompt.lower().strip()

    def pick(name: str) -> str:
        """Find the best matching sample name"""
        name = name.strip()
        # Exact match first
        for s in samples:
            if s.lower() == name.lower():
                return s
        # Partial match
        for s in samples:
            if name.lower() in s.lower():
                return s
        # Return as-is if no match
        return name

    # Look for kDa ranges
    kda = re.search(r'(\d+)\s*[–\-]\s*(\d+)\s*kda', p)
    # Look for bp ranges  
    bp = re.search(r'(\d+)\s*[–\-]\s*(\d+)\s*bp', p)
    
    # Look for comparison patterns
    cmpm = re.search(r'(?:compare|vs\.?|over)\s+([a-z0-9_.\s-]+?)\s+(?:vs\.?|over)\s+([a-z0-9_.\s-]+?)(?:\s|$|in|between)', p) \
        or re.search(r'fold\s*change\s+([a-z0-9_.\s-]+?)\s+over\s+([a-z0-9_.\s-]+?)(?:\s|$|in|between)', p)

    # Build basic recipe structure
    recipe = {
        "id": "",
        "name": "nl_recipe", 
        "pipeline": [{"op": "filter_confidence", "min": 0.30}]
    }
    
    # Add range filtering if specified
    if kda:
        lo, hi = float(kda.group(1)), float(kda.group(2))
        recipe["pipeline"].append({"op": "filter_range", "col": "kDa", "min": lo, "max": hi})
    if bp:
        lo, hi = float(bp.group(1)), float(bp.group(2))
        recipe["pipeline"].append({"op": "filter_range", "col": "bp", "min": lo, "max": hi})

    # Add grouping step
    recipe["pipeline"].append({"op": "group_sum", "by": "sample", "col": "intensity"})

    # Handle comparisons
    if cmpm:
        A = pick(cmpm.group(1).strip())
        B = pick(cmpm.group(2).strip())
        
        # Determine numerator and denominator based on language
        if "over" in p and "fold" in p:
            numerator, denominator = A, B
        else:
            numerator, denominator = B, A
            
        recipe["pipeline"].append({
            "op": "fold_change",
            "numerator": numerator,
            "denominator": denominator,
            "log2": True
        })
        return recipe

    # Handle percent requests
    if "percent of lane total" in p or "percent of sample total" in p:
        recipe["pipeline"].append({"op": "percent_of_total", "by": "sample"})
        return recipe

    # If we found range filtering but no specific comparison, return the basic recipe
    if kda or bp:
        return recipe

    # Couldn't parse into a meaningful recipe
    return None


def execute(recipe: Dict[str, Any], data) -> Dict[str, Any]:
    """
    Simple executor for NLP-generated recipes using pandas operations.
    This is a simplified version that works with the basic operations.
    """
    import pandas as pd
    import numpy as np
    
    df = data.copy()
    ctx = {"metrics": {}, "aggregates": {}}
    
    for step in recipe.get("pipeline", []):
        op = step["op"]
        
        if op == "filter_confidence":
            min_conf = step.get("min", 0.0)
            if "confidence" in df.columns:
                df = df[df["confidence"] >= min_conf].copy()
                
        elif op == "filter_range":
            col = step["col"]
            min_val = step.get("min")
            max_val = step.get("max")
            if col in df.columns:
                mask = pd.Series([True] * len(df))
                if min_val is not None:
                    mask &= (df[col] >= min_val)
                if max_val is not None:
                    mask &= (df[col] <= max_val)
                df = df[mask].copy()
                
        elif op == "group_sum":
            by = step["by"]
            col = step["col"]
            if by in df.columns and col in df.columns:
                grouped = df.groupby(by)[col].sum()
                ctx["aggregates"]["sum_by_sample"] = grouped.to_dict()
                
        elif op == "fold_change":
            numerator = step["numerator"]
            denominator = step["denominator"] 
            log2 = step.get("log2", False)
            
            sums = ctx["aggregates"].get("sum_by_sample", {})
            num_val = sums.get(numerator, 0)
            den_val = sums.get(denominator, 0)
            
            if den_val > 0:
                fc = num_val / den_val
                ctx["metrics"]["fold_change"] = fc
                if log2 and fc > 0:
                    ctx["metrics"]["log2_fc"] = np.log2(fc)
            else:
                ctx["metrics"]["fold_change"] = None
                ctx["metrics"]["log2_fc"] = None
                
        elif op == "percent_of_total":
            by = step["by"]
            if "sum_by_sample" in ctx["aggregates"]:
                sums = ctx["aggregates"]["sum_by_sample"]
                total = sum(sums.values())
                if total > 0:
                    ctx["aggregates"]["percent_by_sample"] = {
                        k: (v / total) * 100 for k, v in sums.items()
                    }
    
    return {"data": df, "ctx": ctx}


def validate_recipe(recipe: Dict[str, Any]) -> List[str]:
    """
    Validate a recipe for basic safety and structure.
    Returns list of error messages (empty if valid).
    """
    errors = []
    
    if not isinstance(recipe, dict):
        errors.append("Recipe must be a dictionary")
        return errors
        
    if "pipeline" not in recipe:
        errors.append("Recipe must have a 'pipeline' field")
        return errors
        
    pipeline = recipe["pipeline"]
    if not isinstance(pipeline, list):
        errors.append("Pipeline must be a list")
        return errors
        
    valid_ops = {
        "filter_confidence", "filter_range", "group_sum", 
        "fold_change", "percent_of_total"
    }
    
    for i, step in enumerate(pipeline):
        if not isinstance(step, dict):
            errors.append(f"Step {i+1} must be a dictionary")
            continue
            
        if "op" not in step:
            errors.append(f"Step {i+1} missing 'op' field")
            continue
            
        if step["op"] not in valid_ops:
            errors.append(f"Step {i+1} has unknown operation: {step['op']}")
            
    return errors