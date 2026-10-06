#!/usr/bin/env python3
"""Render release/update.json from scripts/update.json.template.

Usage:
  python scripts/render_update_json.py --version 1.0.0 --owner GITHUB_OWNER
versionCode is derived as MAJOR*10000 + MINOR*100 + PATCH.
"""
import argparse
import os
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def code_for(version):
    parts = version.split(".")
    if len(parts) != 3 or not all(p.isdigit() for p in parts):
        raise SystemExit("version must be MAJOR.MINOR.PATCH numeric, got %r" % version)
    major, minor, patch = (int(p) for p in parts)
    return major * 10000 + minor * 100 + patch


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--version", required=True)
    ap.add_argument("--owner", required=True)
    args = ap.parse_args()
    version = args.version.lstrip("v")
    code = code_for(version)
    with open(os.path.join(REPO, "scripts", "update.json.template"),
              "r", encoding="utf-8") as fh:
        tpl = fh.read()
    out = (tpl.replace("__VERSION__", version)
              .replace("__VERSION_CODE__", str(code))
              .replace("__OWNER__", args.owner))
    dest = os.path.join(REPO, "release", "update.json")
    with open(dest, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(out)
    print("rendered %s (versionCode %d)" % (dest, code))


if __name__ == "__main__":
    main()
