param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string] $Target,

    [int] $TimeoutSeconds = 180
)

$ErrorActionPreference = "Stop"

$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Gradle = Join-Path $Root "gradlew.bat"
$Log = Join-Path $Root "run\$Target\logs\latest.log"
$SmokeDirectory = Join-Path $Root "build\smoke"
$Stdout = Join-Path $SmokeDirectory "$Target.stdout.log"
$Stderr = Join-Path $SmokeDirectory "$Target.stderr.log"
$StartedAt = Get-Date

New-Item -ItemType Directory -Path $SmokeDirectory -Force | Out-Null
Remove-Item -LiteralPath $Stdout, $Stderr -Force -ErrorAction SilentlyContinue

Write-Host "Runtime smoke: $Target"
$Process = Start-Process `
    -FilePath $Gradle `
    -ArgumentList @("--configure-on-demand", "--console=plain", ":${Target}:runClient") `
    -WorkingDirectory $Root `
    -WindowStyle Hidden `
    -RedirectStandardOutput $Stdout `
    -RedirectStandardError $Stderr `
    -PassThru

function Stop-ProcessTree([int] $RootProcessId) {
    $AllProcesses = Get-CimInstance Win32_Process
    $Ids = [System.Collections.Generic.List[int]]::new()

    function Add-Children([int] $ParentProcessId) {
        foreach ($Child in $AllProcesses | Where-Object ParentProcessId -eq $ParentProcessId) {
            Add-Children $Child.ProcessId
            $Ids.Add($Child.ProcessId)
        }
    }

    Add-Children $RootProcessId
    $Ids.Add($RootProcessId)
    foreach ($Id in $Ids) {
        Stop-Process -Id $Id -Force -ErrorAction SilentlyContinue
    }
}

try {
    $Deadline = $StartedAt.AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $Deadline) {
        if (Test-Path $Log) {
            $LogFile = Get-Item $Log
            if ($LogFile.LastWriteTime -ge $StartedAt.AddSeconds(-2)) {
                $Contents = Get-Content $Log -Raw -ErrorAction SilentlyContinue
                $RuntimeReady = $Contents -match "Initialized Simple Voice Voice Changer client runtime"
                $PluginReady = $Contents -match "Registered the Simple Voice Chat audio processor"
                $AudioReady = $Contents -match "OpenAL initialized|Sound engine started"
                $FatalError = $Contents -match "Mixin apply failed|MixinTransformerError|The game crashed"

                if ($FatalError) {
                    throw "Fatal startup error in $Log"
                }

                if ($RuntimeReady -and $PluginReady -and $AudioReady) {
                    Write-Host "PASS $Target"
                    return
                }
            }
        }

        if ($Process.HasExited) {
            $Tail = if (Test-Path $Log) { (Get-Content $Log -Tail 60) -join [Environment]::NewLine } else { "No Minecraft log was created." }
            throw "Client exited before smoke markers for $Target.`n$Tail"
        }

        Start-Sleep -Seconds 1
        $Process.Refresh()
    }

    $Tail = if (Test-Path $Log) { (Get-Content $Log -Tail 60) -join [Environment]::NewLine } else { "No Minecraft log was created." }
    throw "Timed out after $TimeoutSeconds seconds waiting for $Target.`n$Tail"
} finally {
    Stop-ProcessTree $Process.Id
}
