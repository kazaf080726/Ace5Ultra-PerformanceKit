# One-command release (thin wrapper around bump_release.py).
# Example: scripts\bump-release.ps1 1.0.1
$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$py = (Get-Command python -ErrorAction SilentlyContinue)
if (-not $py) { $py = (Get-Command python3 -ErrorAction SilentlyContinue) }
if (-not $py) { throw "python is required to run the release script" }
& $py.Source (Join-Path $scriptDir 'bump_release.py') @args
exit $LASTEXITCODE
