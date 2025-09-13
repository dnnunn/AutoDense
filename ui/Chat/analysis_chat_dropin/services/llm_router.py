"""
Lightweight LLM router with OpenAI-compatible clients (works with OpenAI or LM Studio).
- Honors OPENAI_API_KEY if set.
- Honors OPENAI_BASE_URL (e.g., http://127.0.0.1:1234/v1 for LM Studio).
- Honors OPENAI_MODEL (defaults to "gpt-4o-mini" or any local GGUF-exposed model name).
"""
from __future__ import annotations

import os
from typing import List, Dict, Any, Optional

try:
    # Requires: pip install openai>=1.0.0
    from openai import OpenAI
except Exception as e:  # pragma: no cover
    OpenAI = None  # type: ignore


class LLMRouter:
    def __init__(self, model: Optional[str] = None, base_url: Optional[str] = None, api_key: Optional[str] = None):
        self.model = model or os.getenv("OPENAI_MODEL", "gpt-4o-mini")
        self.base_url = base_url or os.getenv("OPENAI_BASE_URL")  # LM Studio: http://127.0.0.1:1234/v1
        self.api_key = api_key or os.getenv("OPENAI_API_KEY", "not-set")
        self._client = None
        if OpenAI is not None:
            # If base_url is provided, OpenAI client will route to local server (LM Studio supports OpenAI-compatible API)
            self._client = OpenAI(base_url=self.base_url, api_key=self.api_key)

    def _ensure_client(self):
        if self._client is None:
            raise RuntimeError(
                "OpenAI client not available. Install 'openai' package and/or set OPENAI_BASE_URL/OPENAI_API_KEY."
            )

    def chat(
        self,
        messages: List[Dict[str, str]],
        temperature: float = 0.2,
        json_mode: bool = False,
    ) -> str:
        """
        Basic chat call.
        If json_mode=True, we request JSON response for tool routing (model must support JSON).
        """
        self._ensure_client()
        kwargs: Dict[str, Any] = {
            "model": self.model,
            "messages": messages,
            "temperature": temperature,
        }
        if json_mode:
            # Ask the model to strictly return a single JSON object when we want tool calls.
            kwargs["response_format"] = {"type": "json_object"}

        resp = self._client.chat.completions.create(**kwargs)
        content = resp.choices[0].message.content or ""
        return content
