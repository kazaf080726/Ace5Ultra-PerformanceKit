#!/usr/bin/env sh
# One-command release (thin wrapper around bump_release.py).
# Example: scripts/bump-release.sh 1.0.1
set -eu
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PYTHON="${PYTHON:-python3}"
command -v "$PYTHON" >/dev/null 2>&1 || PYTHON=python
exec "$PYTHON" "$SCRIPT_DIR/bump_release.py" "$@"
