#!/usr/bin/env sh
# Build the flashable module zip (thin wrapper around build_module.py).
set -eu
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PYTHON="${PYTHON:-python3}"
command -v "$PYTHON" >/dev/null 2>&1 || PYTHON=python
exec "$PYTHON" "$SCRIPT_DIR/build_module.py" "$@"
