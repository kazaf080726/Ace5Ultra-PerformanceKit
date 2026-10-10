#!/usr/bin/env python3
"""Render release/update.json (module channel) and release/app-update.json
(app-only channel) from the templates in scripts/.

Usage:
  python scripts/render_update_json.py --version 1.0.1 --owner GITHUB_OWNER
versionCode is derived as MAJOR*10000 + MINOR*100 + PATCH.
"""
import argparse
import os

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def code_for(version):
    parts = version.split(".")
    if len(parts) != 3 or not all(p.isdigit() for p in parts):
        raise SystemExit("version must be MAJOR.MINOR.PATCH numeric, got %r" % version)
    major, minor, patch = (int(p) for p in parts)
    return major * 10000 + minor * 100 + patch


def render(template_name, dest_name, version, code, owner):
    with open(os.path.join(REPO, "scripts", template_name), "r", encoding="utf-8") as fh:
        tpl = fh.read()
    out = (tpl.replace("__VERSION__", version)
              .replace("__VERSION_CODE__", str(code))
              .replace("__OWNER__", owner))
    dest = os.path.join(REPO, "release", dest_name)
    with open(dest, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(out)
    print("rendered %s (versionCode %d)" % (dest, code))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--version", required=True)
    ap.add_argument("--owner", required=True)
    args = ap.parse_args()
    version = args.version.lstrip("v")
    code = code_for(version)
    render("update.json.template", "update.json", version, code, args.owner)
    render("app-update.json.template", "app-update.json", version, code, args.owner)


if __name__ == "__main__":
    main()
