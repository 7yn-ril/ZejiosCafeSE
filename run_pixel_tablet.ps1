# This script has been renamed to run_tablet.ps1.
# This shim forwards all arguments to the new script for backwards compatibility.
& (Join-Path $PSScriptRoot "run_tablet.ps1") @args
