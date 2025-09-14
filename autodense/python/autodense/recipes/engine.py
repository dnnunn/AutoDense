# autodense/recipes/engine.py
from typing import Dict, Any, List, Optional, Callable
import json
from pathlib import Path
from dataclasses import dataclass, asdict
from datetime import datetime

from .ops import SafeOps, OperationResult

@dataclass
class RecipeStep:
    """Single step in a custom recipe"""
    operation: str
    parameters: Dict[str, Any]
    input_source: str = "previous"  # "previous", "original", or specific step ID
    step_id: Optional[str] = None

@dataclass 
class Recipe:
    """Complete custom analytical recipe"""
    name: str
    description: str
    steps: List[RecipeStep]
    metadata: Dict[str, Any] = None
    created_at: Optional[str] = None
    
    def __post_init__(self):
        if self.metadata is None:
            self.metadata = {}
        if self.created_at is None:
            self.created_at = datetime.now().isoformat()

@dataclass
class RecipeResult:
    """Result of executing a recipe"""
    success: bool
    final_result: Any
    step_results: List[OperationResult]
    execution_log: List[str]
    recipe_name: str
    executed_at: str

class RecipeEngine:
    """Engine for validating and executing custom analytical recipes"""
    
    def __init__(self):
        self.available_ops = self._get_available_operations()
        
    def _get_available_operations(self) -> Dict[str, Callable]:
        """Get all available operations from SafeOps"""
        ops = {}
        for name in dir(SafeOps):
            if not name.startswith('_'):
                attr = getattr(SafeOps, name)
                if callable(attr):
                    ops[name] = attr
        return ops
    
    def validate_recipe(self, recipe: Recipe) -> OperationResult:
        """Validate a recipe for safety and correctness"""
        try:
            errors = []
            warnings = []
            
            # Check recipe structure
            if not recipe.name or not recipe.steps:
                errors.append("Recipe must have name and at least one step")
            
            # Validate each step
            step_ids = set()
            for i, step in enumerate(recipe.steps):
                step_prefix = f"Step {i+1}"
                
                # Check operation exists
                if step.operation not in self.available_ops:
                    errors.append(f"{step_prefix}: Unknown operation '{step.operation}'")
                
                # Check step ID uniqueness
                if step.step_id:
                    if step.step_id in step_ids:
                        errors.append(f"{step_prefix}: Duplicate step ID '{step.step_id}'")
                    else:
                        step_ids.add(step.step_id)
                
                # Validate input source references
                if step.input_source not in ["previous", "original"] and step.input_source not in step_ids:
                    warnings.append(f"{step_prefix}: Input source '{step.input_source}' not yet defined")
                
                # Check required parameters (basic validation)
                if not isinstance(step.parameters, dict):
                    errors.append(f"{step_prefix}: Parameters must be a dictionary")
            
            if errors:
                return OperationResult(False, None, f"Validation failed: {'; '.join(errors)}", warnings)
            
            return OperationResult(True, None, "Recipe validation passed", warnings)
            
        except Exception as e:
            return OperationResult(False, None, f"Recipe validation error: {str(e)}")
    
    def execute_recipe(self, recipe: Recipe, input_data: Any) -> RecipeResult:
        """Execute a recipe with given input data"""
        execution_log = []
        step_results = []
        current_data = input_data
        data_history = {"original": input_data}
        
        executed_at = datetime.now().isoformat()
        execution_log.append(f"Starting recipe '{recipe.name}' at {executed_at}")
        
        try:
            # Validate recipe first
            validation = self.validate_recipe(recipe)
            if not validation.success:
                return RecipeResult(
                    success=False,
                    final_result=None,
                    step_results=[validation],
                    execution_log=execution_log + [f"Recipe validation failed: {validation.message}"],
                    recipe_name=recipe.name,
                    executed_at=executed_at
                )
            
            # Execute each step
            for i, step in enumerate(recipe.steps):
                step_log = f"Step {i+1} ({step.operation})"
                execution_log.append(f"Executing {step_log}")
                
                # Determine input data for this step
                if step.input_source == "previous":
                    step_input = current_data
                elif step.input_source == "original":
                    step_input = input_data
                elif step.input_source in data_history:
                    step_input = data_history[step.input_source]
                else:
                    error_msg = f"Invalid input source: {step.input_source}"
                    execution_log.append(f"ERROR: {error_msg}")
                    return RecipeResult(
                        success=False,
                        final_result=None,
                        step_results=step_results,
                        execution_log=execution_log,
                        recipe_name=recipe.name,
                        executed_at=executed_at
                    )
                
                # Execute the operation
                operation_func = self.available_ops[step.operation]
                try:
                    # Pass input data as first argument, then unpack parameters
                    if step.parameters:
                        result = operation_func(step_input, **step.parameters)
                    else:
                        result = operation_func(step_input)
                    
                    step_results.append(result)
                    
                    if result.success:
                        current_data = result.data
                        if step.step_id:
                            data_history[step.step_id] = current_data
                        
                        execution_log.append(f"✓ {step_log}: {result.message}")
                        if result.warnings:
                            for warning in result.warnings:
                                execution_log.append(f"  WARNING: {warning}")
                    else:
                        execution_log.append(f"✗ {step_log} FAILED: {result.message}")
                        return RecipeResult(
                            success=False,
                            final_result=None,
                            step_results=step_results,
                            execution_log=execution_log,
                            recipe_name=recipe.name,
                            executed_at=executed_at
                        )
                        
                except Exception as e:
                    error_msg = f"Exception in {step_log}: {str(e)}"
                    execution_log.append(f"✗ {error_msg}")
                    step_results.append(OperationResult(False, None, error_msg))
                    return RecipeResult(
                        success=False,
                        final_result=None,
                        step_results=step_results,
                        execution_log=execution_log,
                        recipe_name=recipe.name,
                        executed_at=executed_at
                    )
            
            execution_log.append(f"Recipe '{recipe.name}' completed successfully")
            
            return RecipeResult(
                success=True,
                final_result=current_data,
                step_results=step_results,
                execution_log=execution_log,
                recipe_name=recipe.name,
                executed_at=executed_at
            )
            
        except Exception as e:
            execution_log.append(f"FATAL ERROR: {str(e)}")
            return RecipeResult(
                success=False,
                final_result=None,
                step_results=step_results,
                execution_log=execution_log,
                recipe_name=recipe.name,
                executed_at=executed_at
            )
    
    def save_recipe(self, recipe: Recipe, filepath: Path) -> OperationResult:
        """Save recipe to JSON file"""
        try:
            filepath.parent.mkdir(parents=True, exist_ok=True)
            with open(filepath, 'w') as f:
                json.dump(asdict(recipe), f, indent=2)
            return OperationResult(True, str(filepath), f"Recipe saved to {filepath}")
        except Exception as e:
            return OperationResult(False, None, f"Failed to save recipe: {str(e)}")
    
    def load_recipe(self, filepath: Path) -> OperationResult:
        """Load recipe from JSON file"""
        try:
            with open(filepath, 'r') as f:
                data = json.load(f)
            
            # Convert steps back to RecipeStep objects
            steps = [RecipeStep(**step) for step in data['steps']]
            data['steps'] = steps
            
            recipe = Recipe(**data)
            return OperationResult(True, recipe, f"Recipe loaded from {filepath}")
        except Exception as e:
            return OperationResult(False, None, f"Failed to load recipe: {str(e)}")
    
    def get_operation_info(self) -> Dict[str, Dict[str, str]]:
        """Get information about available operations"""
        op_info = {}
        for name, func in self.available_ops.items():
            doc = func.__doc__ or "No description available"
            # Extract first line of docstring
            description = doc.split('\n')[0].strip('"""').strip()
            op_info[name] = {
                "description": description,
                "function_name": name
            }
        return op_info