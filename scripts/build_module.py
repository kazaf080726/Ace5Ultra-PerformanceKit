#!/usr/bin/env python3
"""Build the flashable Ace5Ultra-PerformanceKit module zip.

Reads the module sources from <repo>/module, applies Unix permission bits so the zip is
correct even when built on Windows, and writes
<repo>/release/ace5ultra_perfkit-v<version>.zip.

This script is intentionally dependency-free and portable; it uses only the standard
library. It never writes outside the repository.
"""
import os
import stat
import sys
import zipfile

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(REPO, "module")
OUT = os.path.join(REPO, "release")

JUNK = {".DS_Store", "Thumbs.db", "desktop.ini"}
JUNK_EXT = (".swp", ".pyc", ".log")
# Paths (relative to module root, forward slashes) that must be executable.
EXEC_NAMES = {
    "customize.sh",
    "post-fs-data.sh",
    "service.sh",
    "action.sh",
    "uninstall.sh",
    os.path.join("META-INF", "com", "google", "android", "update-binary"),
}
EXEC_DIRS = {"bin"}
# Normalize all executable path keys to forward slashes for cross-platform matching.
EXEC_NAMES = {p.replace(os.sep, "/") for p in EXEC_NAMES}


def read_version():
    prop = os.path.join(SRC, "module.prop")
    with open(prop, "r", encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if line.startswith("version="):
                return line.split("=", 1)[1].strip()
    raise SystemExit("version= not found in module.prop")


def is_exec(rel):
    rel = rel.replace(os.sep, "/")
    base = os.path.basename(rel)
    if rel in EXEC_NAMES:
        return True
    parts = rel.split("/")
    if parts and parts[0] in EXEC_DIRS:
        # binaries and shell entry points under bin/ are executable
        return base in ("perfctl", "perfd") or base.endswith(".sh")
    return False


def main():
    if not os.path.isdir(SRC):
        raise SystemExit("module/ source directory missing: %s" % SRC)
    version = read_version()
    os.makedirs(OUT, exist_ok=True)
    zip_path = os.path.join(OUT, "ace5ultra_perfkit-v%s.zip" % version)
    if os.path.exists(zip_path):
        os.remove(zip_path)

    entries = []
    for root, dirs, files in os.walk(SRC):
        dirs.sort()
        for fname in sorted(files):
            if fname in JUNK or fname.endswith(JUNK_EXT):
                continue
            abs_path = os.path.join(root, fname)
            rel = os.path.relpath(abs_path, SRC).replace(os.sep, "/")
            entries.append((abs_path, rel))

    fixed_time = (2026, 1, 1, 0, 0, 0)
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
        for abs_path, rel in entries:
            with open(abs_path, "rb") as fh:
                data = fh.read()
            info = zipfile.ZipInfo(rel, fixed_time)
            info.compress_type = zipfile.ZIP_DEFLATED
            mode = 0o755 if is_exec(rel) else 0o644
            info.external_attr = (stat.S_IFREG | mode) << 16
            info.create_system = 3  # Unix
            zf.writestr(info, data)

    print("built %s (%d entries)" % (zip_path, len(entries)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
