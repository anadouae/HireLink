#!/usr/bin/env python3
"""Fail if frontend/legal/*.html is not valid UTF-8 or lacks expected accents / return link."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LEGAL = ROOT / "frontend" / "legal"
REQUIRED = ("cgu.html", "confidentialite.html")


def check(path: Path) -> list[str]:
    errors: list[str] = []
    raw = path.read_bytes()
    if raw.startswith(b"\xff\xfe") or raw.startswith(b"\xfe\xff"):
        errors.append(f"{path.name}: UTF-16 BOM forbidden")
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError as e:
        errors.append(f"{path.name}: not valid UTF-8 ({e})")
        return errors
    if "charset=UTF-8" not in text and "charset=\"utf-8\"" not in text.lower():
        errors.append(f"{path.name}: missing charset=UTF-8 meta")
    if "← Retour" not in text:
        errors.append(f"{path.name}: missing '← Retour' link text")
    # Spot-check that French accents survived (at least one common form)
    if not any(w in text for w in ("général", "données", "confidential", "évaluation", "académique", "personnelles")):
        errors.append(f"{path.name}: expected French accented wording not found")
    # Classic mojibake markers
    for bad in ("Ã©", "Ã¨", "â€™", "Â«", "Â»"):
        if bad in text:
            errors.append(f"{path.name}: mojibake marker {bad!r}")
    return errors


def main() -> int:
    if not LEGAL.is_dir():
        print(f"FAIL: missing {LEGAL}", file=sys.stderr)
        return 2
    all_errors: list[str] = []
    for name in REQUIRED:
        p = LEGAL / name
        if not p.is_file():
            all_errors.append(f"missing file: {name}")
            continue
        all_errors.extend(check(p))
    if all_errors:
        print("LEGAL UTF-8 CHECK FAILED:")
        for e in all_errors:
            print(" -", e)
        return 1
    print("LEGAL UTF-8 CHECK OK:", ", ".join(REQUIRED))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
