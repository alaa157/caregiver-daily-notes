#!/usr/bin/env python3
"""Generate Android string resources from the design copy JSONs.

Source of truth: design/copy.en.json and design/copy.ar.json.
Every user-visible string must come from these files, with identical keys
in identical order. Key names use dots replaced by underscores
(e.g. "nav.home" -> "nav_home"). Values pass through verbatim, including
the app.name placeholder ("[APP NAME - supplied by owner]") and the
home.greeting "{timeOfDay}" template token -- do not invent copy.

Usage (from repo root):
    python3 scripts/gen_strings.py

The script fails loudly if the two JSONs diverge in keys or order.
Generated files must not be hand-edited; re-run this script instead.
"""

import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
COPY_EN = REPO_ROOT / "design" / "copy.en.json"
COPY_AR = REPO_ROOT / "design" / "copy.ar.json"
OUT_EN = REPO_ROOT / "android" / "app" / "src" / "main" / "res" / "values" / "strings.xml"
OUT_AR = REPO_ROOT / "android" / "app" / "src" / "main" / "res" / "values-ar" / "strings.xml"


def load_ordered(path: Path) -> list[tuple[str, str]]:
    with path.open(encoding="utf-8") as f:
        data = json.load(f)
    if not isinstance(data, dict):
        raise SystemExit(f"error: {path} must be a JSON object")
    return list(data.items())


def key_to_name(key: str) -> str:
    return key.replace(".", "_")


def build_xml(pairs: list[tuple[str, str]]) -> str:
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        "<!-- GENERATED from design/copy.*.json by scripts/gen_strings.py. Do not edit by hand. -->",
        "<resources>",
    ]
    for key, value in pairs:
        if not value.strip():
            raise SystemExit(f"error: empty value for key '{key}'")
        el = ET.Element("string", name=key_to_name(key))
        el.text = value
        raw = ET.tostring(el, encoding="unicode")
        # Escape apostrophes the Android way; ET already escapes & < > and quotes.
        raw = raw.replace("'", "\\'")
        lines.append(f"    {raw}")
    lines.append("</resources>")
    return "\n".join(lines) + "\n"


def main() -> int:
    en = load_ordered(COPY_EN)
    ar = load_ordered(COPY_AR)
    en_keys = [k for k, _ in en]
    ar_keys = [k for k, _ in ar]
    if en_keys != ar_keys:
        en_only = [k for k in en_keys if k not in ar_keys]
        ar_only = [k for k in ar_keys if k not in en_keys]
        print("error: copy.en.json and copy.ar.json diverge.", file=sys.stderr)
        if en_only:
            print(f"  en-only: {en_only}", file=sys.stderr)
        if ar_only:
            print(f"  ar-only: {ar_only}", file=sys.stderr)
        if not en_only and not ar_only:
            print("  same key sets, different order.", file=sys.stderr)
            print(f"  en: {en_keys}", file=sys.stderr)
            print(f"  ar: {ar_keys}", file=sys.stderr)
        return 1
    OUT_EN.write_text(build_xml(en), encoding="utf-8")
    OUT_AR.write_text(build_xml(ar), encoding="utf-8")
    print(f"wrote {OUT_EN.relative_to(REPO_ROOT)} ({len(en)} strings)")
    print(f"wrote {OUT_AR.relative_to(REPO_ROOT)} ({len(ar)} strings)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
