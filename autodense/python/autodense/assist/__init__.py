# autodense/assist/__init__.py
from .nlp_intent import parse_prompt_to_recipe
from .label_fitting import place_labels

__all__ = ['parse_prompt_to_recipe', 'place_labels']