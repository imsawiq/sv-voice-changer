param(
    [int] $TimeoutSeconds = 180
)

$ErrorActionPreference = "Stop"

$Versions = @(
    "1.21.1", "1.21.2", "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7",
    "1.21.8", "1.21.9", "1.21.10", "1.21.11", "26.1.x", "26.2.x", "26.3.x"
)
$Loaders = @("fabric", "neoforge")

foreach ($Version in $Versions) {
    foreach ($Loader in $Loaders) {
        & (Join-Path $PSScriptRoot "smoke-version.ps1") "$Version-$Loader" -TimeoutSeconds $TimeoutSeconds
    }
}

Write-Host "All 26 runtime smoke targets passed."
