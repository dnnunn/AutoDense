import os, json, re, shutil, subprocess, tempfile, sys
from pathlib import Path
from typing import Any, Dict
import yaml
import ast
from .schemas import PatchProposal

def _is_git_repo(root: Path) -> bool:
    return (root / ".git").exists()

def _git(*args, cwd: Path):
    return subprocess.run(["git", *args], cwd=str(cwd), check=False, capture_output=True, text=True)

def _backup_file(path: Path):
    backup = path.with_suffix(path.suffix + ".bak")
    shutil.copy2(path, backup)
    return backup

def _set_in_mapping(d: Dict[str, Any], dotted: str, value: Any):
    keys = dotted.split(".")
    cur = d
    for k in keys[:-1]:
        if k not in cur or not isinstance(cur[k], dict):
            cur[k] = {}
        cur = cur[k]
    cur[keys[-1]] = value

class LiteralScaler(ast.NodeTransformer):
    def __init__(self, var_name: str, op: str, factor: float):
        self.var_name = var_name
        self.op = op
        self.factor = factor
        super().__init__()
    def visit_Assign(self, node):
        try:
            if len(node.targets)==1 and isinstance(node.targets[0], ast.Name) and node.targets[0].id == self.var_name:
                if isinstance(node.value, (ast.Num, ast.UnaryOp)) or isinstance(node.value, ast.Constant):
                    try:
                        val = ast.literal_eval(node.value)
                        if isinstance(val, (int, float)):
                            if self.op == "scale":
                                val = val * self.factor
                            elif self.op == "add":
                                val = val + self.factor
                            node.value = ast.copy_location(ast.Constant(val), node.value)
                    except Exception:
                        pass
        except Exception:
            pass
        return node

def apply_patch(patch: PatchProposal, repo_root: str = ".") -> str:
    root = Path(repo_root).resolve()
    target = (root / patch.file).resolve()
    if not target.exists():
        raise FileNotFoundError(f"Patch target not found: {target}")
    if _is_git_repo(root):
        # Create branch
        branch = patch.branch_name
        _git(["checkout","-b", branch], cwd=root)
    else:
        branch = "no-git"
    # Apply
    if patch.kind == "param":
        # YAML/JSON/TOML (TOML not implemented, treat as YAML if possible)
        txt = target.read_text(encoding="utf-8")
        data = None
        try:
            if target.suffix.lower() in (".yaml",".yml"):
                data = yaml.safe_load(txt) or {}
                _set_in_mapping(data, patch.path, patch.to)
                target.write_text(yaml.safe_dump(data, sort_keys=False), encoding="utf-8")
            elif target.suffix.lower() == ".json":
                data = json.loads(txt)
                _set_in_mapping(data, patch.path, patch.to)
                target.write_text(json.dumps(data, indent=2), encoding="utf-8")
            else:
                raise ValueError("Unsupported param file type (use .yaml/.yml/.json)")
        except Exception as e:
            raise
    elif patch.kind == "regex_var":
        pattern = re.compile(rf"^(\s*)({re.escape(patch.path)})\s*=\s*([0-9]*\.?[0-9]+)", re.MULTILINE)
        txt = target.read_text(encoding="utf-8")
        if patch.allowed_vars and patch.path not in patch.allowed_vars:
            raise ValueError(f"Variable {patch.path} not in allowed_vars")
        new_txt, n = pattern.subn(rf"\1\2 = {patch.to}", txt)
        if n == 0:
            raise ValueError(f"Variable assignment not found for {patch.path}")
        target.write_text(new_txt, encoding="utf-8")
    elif patch.kind == "ast":
        src = target.read_text(encoding="utf-8")
        tree = ast.parse(src)
        var = patch.path or "prominence"
        op = patch.op or "scale"
        factor = patch.factor or 1.0
        new_tree = LiteralScaler(var, op, factor).visit(tree)
        new_src = ast.unparse(new_tree) if hasattr(ast, "unparse") else src
        target.write_text(new_src, encoding="utf-8")
    else:
        raise ValueError(f"Unknown patch kind: {patch.kind}")
    # Commit if git
    if _is_git_repo(root):
        _git(["add", patch.file], cwd=root)
        _git(["commit","-m", patch.commit_message], cwd=root)
    return branch

def revert_patch(branch: str, repo_root: str = "."):
    root = Path(repo_root).resolve()
    if _is_git_repo(root) and branch != "no-git":
        _git(["reset","--hard","HEAD~1"], cwd=root)
        _git(["checkout","-"], cwd=root)
