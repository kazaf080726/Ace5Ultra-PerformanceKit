# Build the flashable module zip (thin wrapper around build_module.py).
$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$py = (Get-Command python -ErrorAction SilentlyContinue)
if (-not $py) { $py = (Get-Command python3 -ErrorAction SilentlyContinue) }
if (-not $py) { throw "python is required to build the module zip" }
& $py.Source (Join-Path $scriptDir 'build_module.py') @args
exit $LASTEXITCODE
