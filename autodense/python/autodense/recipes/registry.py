# autodense/recipes/registry.py
from typing import Dict, List, Optional
from pathlib import Path
import json
from datetime import datetime

from .engine import Recipe, RecipeEngine, RecipeStep, OperationResult

class RecipeRegistry:
    """Registry for managing preset and custom recipes"""
    
    def __init__(self, recipes_dir: Optional[Path] = None):
        self.recipes_dir = recipes_dir or Path("recipes")
        self.recipes_dir.mkdir(exist_ok=True)
        self.engine = RecipeEngine()
        self._built_in_presets = self._create_built_in_presets()
    
    def _create_built_in_presets(self) -> Dict[str, Recipe]:
        """Create built-in recipe presets"""
        presets = {}
        
        # Normalization preset
        presets["normalize_to_control"] = Recipe(
            name="Normalize to Control",
            description="Normalize all values to the first lane (control)",
            steps=[
                RecipeStep(
                    operation="normalize_to_control",
                    parameters={"control_idx": 0},
                    step_id="normalized"
                ),
                RecipeStep(
                    operation="statistical_summary",
                    parameters={},
                    input_source="normalized"
                )
            ],
            metadata={"category": "normalization", "built_in": True}
        )
        
        # Quality control preset
        presets["quality_control"] = Recipe(
            name="Quality Control Filter",
            description="Filter outliers and generate QC statistics",
            steps=[
                RecipeStep(
                    operation="outlier_detection",
                    parameters={"method": "iqr", "threshold": 1.5},
                    step_id="outliers"
                ),
                RecipeStep(
                    operation="quality_filter",
                    parameters={"max_cv": 20.0},
                    step_id="filtered"
                ),
                RecipeStep(
                    operation="statistical_summary",
                    parameters={},
                    input_source="filtered"
                )
            ],
            metadata={"category": "quality_control", "built_in": True}
        )
        
        # Log transformation preset
        presets["log2_analysis"] = Recipe(
            name="Log2 Transformation Analysis", 
            description="Apply log2 transformation and calculate statistics",
            steps=[
                RecipeStep(
                    operation="log_transform",
                    parameters={"base": 2.0},
                    step_id="log_transformed"
                ),
                RecipeStep(
                    operation="statistical_summary",
                    parameters={},
                    input_source="log_transformed"
                ),
                RecipeStep(
                    operation="outlier_detection",
                    parameters={"method": "zscore", "threshold": 2.0},
                    input_source="log_transformed"
                )
            ],
            metadata={"category": "transformation", "built_in": True}
        )
        
        # Fold change analysis preset
        presets["fold_change_analysis"] = Recipe(
            name="Fold Change Analysis",
            description="Calculate fold changes between treatment groups (requires manual setup)",
            steps=[
                RecipeStep(
                    operation="statistical_summary",
                    parameters={},
                    step_id="baseline_stats"
                )
                # Note: fold_change operation requires two separate inputs,
                # so this is a template that users need to customize
            ],
            metadata={"category": "comparison", "built_in": True, "template": True}
        )
        
        return presets
    
    def get_preset(self, preset_name: str) -> Optional[Recipe]:
        """Get a built-in preset recipe"""
        return self._built_in_presets.get(preset_name)
    
    def list_presets(self) -> List[str]:
        """List available built-in presets"""
        return list(self._built_in_presets.keys())
    
    def get_preset_info(self) -> Dict[str, Dict[str, str]]:
        """Get information about all presets"""
        info = {}
        for name, recipe in self._built_in_presets.items():
            info[name] = {
                "name": recipe.name,
                "description": recipe.description,
                "category": recipe.metadata.get("category", "general"),
                "template": recipe.metadata.get("template", False)
            }
        return info
    
    def save_custom_recipe(self, recipe: Recipe, overwrite: bool = False) -> OperationResult:
        """Save a custom recipe to the registry"""
        try:
            # Sanitize filename
            filename = "".join(c for c in recipe.name if c.isalnum() or c in (' ', '-', '_')).rstrip()
            filename = filename.replace(' ', '_').lower() + '.json'
            filepath = self.recipes_dir / filename
            
            if filepath.exists() and not overwrite:
                return OperationResult(False, None, f"Recipe '{recipe.name}' already exists. Use overwrite=True to replace.")
            
            # Mark as custom recipe
            if not recipe.metadata:
                recipe.metadata = {}
            recipe.metadata["built_in"] = False
            recipe.metadata["custom"] = True
            recipe.metadata["saved_at"] = datetime.now().isoformat()
            
            result = self.engine.save_recipe(recipe, filepath)
            if result.success:
                return OperationResult(True, str(filepath), f"Custom recipe '{recipe.name}' saved")
            else:
                return result
                
        except Exception as e:
            return OperationResult(False, None, f"Failed to save custom recipe: {str(e)}")
    
    def load_custom_recipe(self, recipe_name: str) -> OperationResult:
        """Load a custom recipe from the registry"""
        try:
            # Try exact filename first
            filename = recipe_name if recipe_name.endswith('.json') else f"{recipe_name}.json"
            filepath = self.recipes_dir / filename
            
            if not filepath.exists():
                # Try sanitized filename
                filename = "".join(c for c in recipe_name if c.isalnum() or c in (' ', '-', '_')).rstrip()
                filename = filename.replace(' ', '_').lower() + '.json'
                filepath = self.recipes_dir / filename
                
            if not filepath.exists():
                return OperationResult(False, None, f"Custom recipe '{recipe_name}' not found")
            
            return self.engine.load_recipe(filepath)
            
        except Exception as e:
            return OperationResult(False, None, f"Failed to load custom recipe: {str(e)}")
    
    def list_custom_recipes(self) -> List[Dict[str, str]]:
        """List all custom recipes in the registry"""
        recipes = []
        try:
            for filepath in self.recipes_dir.glob("*.json"):
                try:
                    result = self.engine.load_recipe(filepath)
                    if result.success:
                        recipe = result.data
                        recipes.append({
                            "name": recipe.name,
                            "description": recipe.description,
                            "filename": filepath.name,
                            "created_at": recipe.created_at or "unknown",
                            "steps": len(recipe.steps)
                        })
                except Exception:
                    continue  # Skip invalid recipe files
        except Exception:
            pass  # Directory doesn't exist or other error
        
        return recipes
    
    def delete_custom_recipe(self, recipe_name: str) -> OperationResult:
        """Delete a custom recipe from the registry"""
        try:
            filename = recipe_name if recipe_name.endswith('.json') else f"{recipe_name}.json"
            filepath = self.recipes_dir / filename
            
            if not filepath.exists():
                # Try sanitized filename
                filename = "".join(c for c in recipe_name if c.isalnum() or c in (' ', '-', '_')).rstrip()
                filename = filename.replace(' ', '_').lower() + '.json'
                filepath = self.recipes_dir / filename
            
            if not filepath.exists():
                return OperationResult(False, None, f"Custom recipe '{recipe_name}' not found")
            
            filepath.unlink()
            return OperationResult(True, None, f"Custom recipe '{recipe_name}' deleted")
            
        except Exception as e:
            return OperationResult(False, None, f"Failed to delete custom recipe: {str(e)}")
    
    def promote_to_preset(self, recipe: Recipe, preset_name: str) -> OperationResult:
        """Promote a custom recipe to built-in preset status (for admin use)"""
        try:
            # Validate recipe first
            validation = self.engine.validate_recipe(recipe)
            if not validation.success:
                return OperationResult(False, None, f"Cannot promote invalid recipe: {validation.message}")
            
            # Mark as built-in preset
            recipe.metadata = recipe.metadata or {}
            recipe.metadata["built_in"] = True
            recipe.metadata["promoted_at"] = datetime.now().isoformat()
            
            # Add to built-in presets
            self._built_in_presets[preset_name] = recipe
            
            return OperationResult(True, preset_name, f"Recipe promoted to preset '{preset_name}'")
            
        except Exception as e:
            return OperationResult(False, None, f"Failed to promote recipe: {str(e)}")
    
    def execute_recipe(self, recipe_identifier: str, input_data: any, is_preset: bool = None) -> OperationResult:
        """Execute a recipe by name (auto-detects preset vs custom)"""
        try:
            recipe = None
            
            # If type is specified, use it
            if is_preset is True:
                recipe = self.get_preset(recipe_identifier)
                if not recipe:
                    return OperationResult(False, None, f"Preset '{recipe_identifier}' not found")
            elif is_preset is False:
                result = self.load_custom_recipe(recipe_identifier)
                if not result.success:
                    return result
                recipe = result.data
            else:
                # Auto-detect: try preset first, then custom
                recipe = self.get_preset(recipe_identifier)
                if not recipe:
                    result = self.load_custom_recipe(recipe_identifier)
                    if not result.success:
                        return OperationResult(False, None, f"Recipe '{recipe_identifier}' not found in presets or custom recipes")
                    recipe = result.data
            
            # Execute the recipe
            execution_result = self.engine.execute_recipe(recipe, input_data)
            
            if execution_result.success:
                return OperationResult(True, execution_result, f"Recipe '{recipe_identifier}' executed successfully")
            else:
                return OperationResult(False, execution_result, f"Recipe '{recipe_identifier}' execution failed")
                
        except Exception as e:
            return OperationResult(False, None, f"Failed to execute recipe: {str(e)}")