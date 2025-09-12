
"""
Tiny, hardened Streamlit uploader for gel workflows.
- Multi-file uploads with explicit reset
- Optional ZIP upload (acts like a directory upload)
- ASCII-safe filename guard (keeps a mapping to original names)
- Returns a uniform list of file records (bytes + metadata)
- Works across Streamlit 1.44 → 1.49+
"""

from __future__ import annotations
import io
import re
import zipfile
import unicodedata
from dataclasses import dataclass, asdict
from typing import List, Optional, Dict, Any, Iterable

import streamlit as st


@dataclass
class FileRecord:
    safe_name: str          # ASCII-safe unique name (for disk / URLs)
    original_name: str      # Original filename (may be non-ASCII)

    bytes: bytes            # File content
    mime: Optional[str]     # Streamlit's detected type if available
    source: str             # 'upload' or 'zip_member'
    path_in_archive: Optional[str] = None  # member path if from zip

    def to_dict(self) -> Dict[str, Any]:
        d = asdict(self)
        d["size_bytes"] = len(self.bytes)
        return d


def _slugify_ascii(name: str) -> str:
    """
    Make an ASCII-safe version of a filename (keep extensions).
    """
    # Split extension(s)
    parts = name.split(".")
    ext = ""
    stem = name
    if len(parts) > 1:
        ext = "." + parts[-1]
        stem = ".".join(parts[:-1])

    # Normalize to NFKD and drop non-ascii
    norm = unicodedata.normalize("NFKD", stem)
    norm = norm.encode("ascii", "ignore").decode("ascii")
    # Replace non-word chars with '-' and collapse repeats
    norm = re.sub(r"[^A-Za-z0-9_-]+", "-", norm).strip("-")
    norm = re.sub(r"-{2,}", "-", norm)
    if not norm:
        norm = "file"
    # Clean ext too
    ext_norm = unicodedata.normalize("NFKD", ext).encode("ascii", "ignore").decode("ascii")
    ext_norm = re.sub(r"[^A-Za-z0-9.]+", "", ext_norm)
    return norm + ext_norm


def _dedupe(names: Iterable[str]) -> Dict[str, str]:
    """
    Deduplicate ASCII-safe names by appending -1, -2, ...
    Returns mapping: original_safe_base -> unique_safe_name
    """
    used = set()
    mapping = {}
    for s in names:
        base, dot, ext = s.partition(".")
        candidate = s
        i = 1
        while candidate in used:
            candidate = f"{base}-{i}{dot}{ext}"
            i += 1
        used.add(candidate)
        mapping[s] = candidate
    return mapping


def render_uploader(
    label: str = "Upload your files",
    *, 
    allowed_exts: Optional[List[str]] = None,
    allow_zip: bool = True,
    accept_multiple_files: bool = True,
    help_text: Optional[str] = None,
    key_root: str = "gel_up",
) -> List[FileRecord]:
    """
    Render an uploader block that supports:
      - multi-file upload
      - optional ZIP upload (for "directory-like" upload)
      - ASCII filename guarding
      - one-click reset

    Returns a list of FileRecord objects.
    """
    if "u_key" not in st.session_state:
        st.session_state.u_key = f"{key_root}_u1"

    def _reset():
        st.session_state.u_key = (
            f"{key_root}_u2" if st.session_state.u_key.endswith("u1") else f"{key_root}_u1"
        )
        st.rerun()

    # UI header
    cols = st.columns([1, 1, 1])
    with cols[0]:
        st.caption("Drop files here. For folders, upload a ZIP (recommended).")
    with cols[1]:
        if st.button("Reset uploader", use_container_width=True):
            _reset()
    with cols[2]:
        st.caption("Non‑ASCII names are normalized for portability.")

    # Primary multi-file uploader
    uploaded = st.file_uploader(
        label,
        type=allowed_exts,
        accept_multiple_files=accept_multiple_files,
        help=help_text,
        key=st.session_state.u_key,
    )

    # Optional ZIP uploader for "directory"
    zip_buf = None
    if allow_zip:
        zip_file = st.file_uploader(
            "Optional: upload a ZIP (treated like a directory)",
            type=["zip"],
            accept_multiple_files=False,
            key=f"{st.session_state.u_key}_zip",
            help="Place a folder into a .zip and upload here. We'll unpack in-memory.",
        )
        if zip_file is not None:
            zip_buf = zip_file.read()

    records: List[FileRecord] = []

    # Handle regular uploads
    if uploaded:
        up_list = uploaded if isinstance(uploaded, list) else [uploaded]
        for f in up_list:
            if f is None:
                continue
            raw = f.read()
            safe = _slugify_ascii(f.name)
            records.append(
                FileRecord(
                    safe_name=safe,
                    original_name=f.name,
                    bytes=raw,
                    mime=getattr(f, "type", None),
                    source="upload",
                )
            )

    # Handle ZIP (directory-like)
    if zip_buf:
        with zipfile.ZipFile(io.BytesIO(zip_buf)) as zf:
            for member in zf.infolist():
                if member.is_dir():
                    continue
                # Read member bytes
                with zf.open(member, "r") as fp:
                    data = fp.read()
                # Build a safe name based on full path (preserve subfolders via '-' join)
                path = member.filename
                # Strip any leading "./"
                path = path[2:] if path.startswith("./") else path
                safe = _slugify_ascii(path.replace("/", "-"))
                records.append(
                    FileRecord(
                        safe_name=safe,
                        original_name=member.filename,
                        bytes=data,
                        mime=None,
                        source="zip_member",
                        path_in_archive=member.filename,
                    )
                )

    # Deduplicate safe names
    safe_names = [r.safe_name for r in records]
    mapping = _dedupe(safe_names)
    for r in records:
        r.safe_name = mapping[r.safe_name]

    return records


def info_table(records: List[FileRecord]) -> str:
    """
    Render a compact JSON preview for logging / debug.
    """
    tiny = [
        {
            "safe_name": r.safe_name,
            "original_name": r.original_name,
            "size_bytes": len(r.bytes),
            "source": r.source,
            "path_in_archive": r.path_in_archive,
            "mime": r.mime,
        }
        for r in records
    ]
    return st.json(tiny)
