param(
    [switch] $NoClean
)

$ErrorActionPreference = "Stop"

$Versions = @("1.21.8", "1.21.11", "26.1.x", "26.2.x", "26.3.x")
$Loaders = @("fabric", "neoforge")
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Dist = Join-Path $Root "dist"

if (Test-Path -LiteralPath $Dist) {
    Get-ChildItem -LiteralPath $Dist -Filter "sv-voice-changer-*.jar" -File |
        Remove-Item -Force
}

foreach ($Version in $Versions) {
    foreach ($Loader in $Loaders) {
        & (Join-Path $PSScriptRoot "build-version.ps1") "$Version-$Loader" -NoClean:$NoClean
        if ($LASTEXITCODE -ne 0) {
            exit $LASTEXITCODE
        }
    }
}

Write-Host "All 8 release artifacts built successfully. The full 26-target matrix remains available for verification."
