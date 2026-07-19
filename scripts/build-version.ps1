param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string] $Target,

    [switch] $NoClean
)

$ErrorActionPreference = "Stop"

$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Versions = @(
    "1.21.1", "1.21.2", "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7",
    "1.21.8", "1.21.9", "1.21.10", "1.21.11", "26.1.x", "26.2.x"
)
$Loaders = @("fabric", "neoforge")

if ($Target -eq "all") {
    & (Join-Path $PSScriptRoot "build-all.ps1") -NoClean:$NoClean
    exit $LASTEXITCODE
}

$Normalized = $Target.ToLowerInvariant()
if ($Normalized -eq "1.21") {
    $Normalized = "1.21.8-fabric"
} elseif ($Normalized -eq "1.21-modern") {
    $Normalized = "1.21.11-fabric"
} elseif ($Normalized -eq "26.1") {
    $Normalized = "26.1.x-fabric"
} elseif ($Normalized -eq "26.2") {
    $Normalized = "26.2.x-fabric"
} elseif ($Normalized.StartsWith("fabric-")) {
    $Normalized = "$($Normalized.Substring(7))-fabric"
} elseif ($Normalized.StartsWith("neoforge-")) {
    $Normalized = "$($Normalized.Substring(9))-neoforge"
} elseif ($Normalized.StartsWith("nf-")) {
    $Normalized = "$($Normalized.Substring(3))-neoforge"
}

$Loader = $Loaders | Where-Object { $Normalized.EndsWith("-$_") } | Select-Object -First 1
if (-not $Loader) {
    throw "Target must include a loader: '<version>-fabric' or '<version>-neoforge'."
}

$Version = $Normalized.Substring(0, $Normalized.Length - $Loader.Length - 1)
if ($Version -eq "1.21") { $Version = "1.21.8" }
if ($Version -eq "26.1") { $Version = "26.1.x" }
if ($Version -eq "26.2") { $Version = "26.2.x" }

if ($Versions -notcontains $Version) {
    throw "Unsupported Minecraft target '$Version'. Supported: $($Versions -join ', ')."
}

$Project = "$Version-$Loader"
$Gradle = Join-Path $Root "gradlew.bat"
$Arguments = @("--configure-on-demand", "--console=plain")
if (-not $NoClean) {
    $Arguments += ":${Project}:clean"
}
$Arguments += ":${Project}:buildAndCollect"

Write-Host "Building $Project"
Push-Location $Root
try {
    & $Gradle @Arguments
    $ExitCode = $LASTEXITCODE
} finally {
    Pop-Location
}

if ($ExitCode -ne 0) {
    exit $ExitCode
}

Write-Host "Built $Project; artifacts are in $(Join-Path $Root 'dist')"
