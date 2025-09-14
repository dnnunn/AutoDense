from typing import List, Dict, Optional, Literal, Any
from dataclasses import dataclass, field
import json

Verdict = Literal["accept", "reject", "revise"]
PatchKind = Literal["param","ast","regex_var"]

@dataclass
class RunReport:
    task: str
    input_path: str
    input_hash: str
    metrics: Dict[str, float]
    diagnostics_png: Optional[str] = None
    meta: Dict[str, Any] = field(default_factory=dict)

    @staticmethod
    def from_json(path: str) -> "RunReport":
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
        return RunReport(**data)

    def to_json(self, path: str) -> None:
        with open(path, "w", encoding="utf-8") as f:
            json.dump(self.__dict__, f, indent=2)

@dataclass
class PatchProposal:
    file: str
    kind: PatchKind
    # dot-notation path for param edits; variable name for regex_var; optional for ast edits
    path: Optional[str] = None
    to: Optional[float] = None
    op: Optional[str] = None
    factor: Optional[float] = None
    commit_message: str = "autotune patch"
    branch_name: str = "autofix/patch"
    allowed_vars: List[str] = field(default_factory=list)

@dataclass
class Critique:
    verdict: Verdict
    reasons: List[str] = field(default_factory=list)
    notes: Optional[str] = None

@dataclass
class Spec:
    task: str
    qc: Dict[str, float]
    acceptance: Dict[str, List[str]]
    budget: Dict[str, int]
    runner: Dict[str, str] = field(default_factory=dict)
    search: Dict[str, Any] = field(default_factory=dict)
