#!/usr/bin/env python3
"""One-command release for Ace5Ultra-PerformanceKit.

Does the whole release dance, cross-platform, with no hard-coded absolute paths:
  1. validate the new x.y.z version and compute versionCode (MAJOR*10000+MINOR*100+PATCH)
  2. resolve the GitHub owner from git remote `origin`, else `gh api user`
  3. bump module/module.prop and the app build file (versionCode/versionName)
  4. regenerate release/update.json from scripts/update.json.template
  5. refresh release/changelog.md from the matching CHANGELOG.md section
  6. build the module zip (build_module.py) and, unless skipped, the release APK
  7. git commit + tag, push so the raw changelog URL resolves
  8. create the GitHub Release uploading the module zip, APK(s) and changelog

Usage:
  python scripts/bump_release.py 1.0.1
  python scripts/bump_release.py 1.1.0 --skip-apk --dry-run
"""
import argparse
import glob
import os
import re
import subprocess
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def run(cmd, **kw):
    print("  $ " + " ".join(cmd))
    return subprocess.run(cmd, cwd=REPO, check=True, text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.STDOUT, **kw)


def run_visible(cmd, **kw):
    print("  $ " + " ".join(cmd))
    return subprocess.run(cmd, cwd=REPO, check=True, **kw)


def code_for(version):
    parts = version.split(".")
    if len(parts) != 3 or not all(p.isdigit() for p in parts):
        raise SystemExit("version must be MAJOR.MINOR.PATCH numeric, got %r" % version)
    major, minor, patch = (int(p) for p in parts)
    return major * 10000 + minor * 100 + patch


def resolve_owner():
    try:
        out = run(["git", "remote", "get-url", "origin"]).stdout
        m = re.search(r"github\.com[:/]([^/]+)/", out)
        if m:
            return m.group(1)
    except Exception:
        pass
    try:
        return run(["gh", "api", "user", "--jq", ".login"]).stdout.strip()
    except Exception:
        raise SystemExit("Cannot determine GitHub owner: no github.com origin remote and "
                         "`gh api user` failed. Run `gh auth login` or add the origin remote.")


def replace_in(path, patterns, repl):
    with open(path, "r", encoding="utf-8") as fh:
        text = fh.read()
    original = text
    for pat in patterns:
        text = pat.sub(repl, text)
    if text == original:
        print("  warning: no match in %s" % path)
    with open(path, "w", encoding="utf-8", newline="") as fh:
        fh.write(text)


def bump_module(version, code):
    path = os.path.join(REPO, "module", "module.prop")
    replace_in(path, [re.compile(r"^version=.*$", re.M)], "version=%s" % version)
    replace_in(path, [re.compile(r"^versionCode=.*$", re.M)], "versionCode=%d" % code)


def bump_app(version, code):
    candidates = glob.glob(os.path.join(REPO, "app", "build.gradle*"))
    if not candidates:
        print("  note: no app build file found, skipping app version bump")
        return
    path = candidates[0]
    replace_in(path, [re.compile(r"versionCode\s*=?\s*\d+")],
               lambda m: re.sub(r"\d+$", str(code), m.group(0)))
    replace_in(path, [re.compile(r'versionName\s*=?\s*"[^"]*"')],
               lambda m: re.sub(r'"[^"]*"', '"%s"' % version, m.group(0)))


def write_update_json(version, code, owner):
    tpl_path = os.path.join(REPO, "scripts", "update.json.template")
    with open(tpl_path, "r", encoding="utf-8") as fh:
        tpl = fh.read()
    out = (tpl.replace("__VERSION__", version)
              .replace("__VERSION_CODE__", str(code))
              .replace("__OWNER__", owner))
    with open(os.path.join(REPO, "release", "update.json"), "w",
              encoding="utf-8", newline="\n") as fh:
        fh.write(out)


def refresh_changelog(version):
    src = os.path.join(REPO, "CHANGELOG.md")
    with open(src, "r", encoding="utf-8") as fh:
        text = fh.read()
    m = re.search(r"^## \[%s\].*?(?=^## \[|\Z)" % re.escape(version),
                  text, re.M | re.S)
    if not m:
        print("  note: no CHANGELOG.md section for %s, keeping existing" % version)
        return
    body = m.group(0).strip()
    body = re.sub(r"^## \[%s\][^\n]*" % re.escape(version),
                  "# Ace5Ultra-PerformanceKit v%s" % version, body)
    with open(os.path.join(REPO, "release", "changelog.md"), "w",
              encoding="utf-8", newline="\n") as fh:
        fh.write(body + "\n")


def build_module_zip():
    py = sys.executable
    run_visible([py, os.path.join(REPO, "scripts", "build_module.py")])


def build_apk():
    wrapper = "gradlew.bat" if os.name == "nt" else "./gradlew"
    if os.name != "nt":
        wrapper_path = os.path.join(REPO, wrapper)
        if os.path.exists(wrapper_path):
            os.chmod(wrapper_path, 0o755)
    run_visible([wrapper, "assembleRelease"], cwd=REPO)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("version")
    ap.add_argument("--skip-apk", action="store_true", help="do not build the release APK")
    ap.add_argument("--dry-run", action="store_true",
                    help="make file changes but do not git push or create a release")
    args = ap.parse_args()

    version = args.version.strip().lstrip("v")
    code = code_for(version)
    print("Releasing v%s (versionCode %d)" % (version, code))

    print("Resolving owner...")
    owner = resolve_owner()
    print("  owner = %s" % owner)

    print("Bumping versions...")
    bump_module(version, code)
    bump_app(version, code)
    write_update_json(version, code, owner)
    refresh_changelog(version)

    print("Building module zip...")
    build_module_zip()
    if not args.skip_apk:
        print("Building release APK...")
        build_apk()

    zip_path = os.path.join(REPO, "release",
                            "ace5ultra_perfkit-v%s.zip" % version)
    assets = [zip_path, os.path.join(REPO, "release", "update.json"),
              os.path.join(REPO, "release", "changelog.md")]
    apks = glob.glob(os.path.join(REPO, "app", "build", "outputs", "apk",
                                  "release", "*.apk"))
    assets.extend(apks)
    for a in assets:
        if not os.path.exists(a):
            raise SystemExit("expected asset missing: %s" % a)

    if args.dry_run:
        print("DRY RUN complete. Assets that would be uploaded:")
        for a in assets:
            print("   " + a)
        return 0

    tag = "v%s" % version
    print("Committing and tagging %s ..." % tag)
    run(["git", "add", "-A"])
    status = run(["git", "status", "--porcelain"]).stdout
    if status.strip():
        run(["git", "commit", "-m", "Release %s" % tag])
    else:
        print("  nothing to commit")
    run(["git", "tag", "-f", tag])
    branch = run(["git", "rev-parse", "--abbrev-ref", "HEAD"]).stdout.strip()
    run(["git", "push", "origin", branch])
    run(["git", "push", "origin", tag, "--force"])

    print("Creating GitHub release...")
    existing = subprocess.run(["gh", "release", "view", tag], cwd=REPO,
                              stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    if existing.returncode == 0:
        run(["gh", "release", "delete", tag, "--yes", "--cleanup-tag"])
    cmd = ["gh", "release", "create", tag, "--title",
           "Ace5Ultra-PerformanceKit %s" % tag,
           "--notes-file", os.path.join(REPO, "release", "changelog.md")]
    cmd.extend(assets)
    run(cmd)
    print("RELEASE COMPLETE: %s" % tag)
    return 0


if __name__ == "__main__":
    sys.exit(main())
