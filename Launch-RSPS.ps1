[CmdletBinding()]
param(
    [switch]$Launch,
    [switch]$SkipClient
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$ClientRoot = $PSScriptRoot
$RspsRoot = Split-Path -Parent (Split-Path -Parent $ClientRoot)
$FileServerRoot = Join-Path $RspsRoot 'file-server'
$GameRoot = Join-Path $RspsRoot 'game\game'
$LogRoot = Join-Path $RspsRoot 'launcher-logs'

$Targets = @(
    [pscustomobject]@{ Name = 'file server'; Root = $FileServerRoot; Port = 50016; Arguments = 'run --no-daemon' },
    [pscustomobject]@{ Name = 'game server'; Root = $GameRoot; Port = 50015; Arguments = ':game:run --no-daemon' }
)

function Test-ListeningPort([int]$Port) {
    # Get-NetTCPConnection may hide listeners owned by another process/user.
    # A loopback connection answers the actual launcher question without
    # requiring process-inspection rights.
    return Test-NetConnection -ComputerName '127.0.0.1' -Port $Port -InformationLevel Quiet -WarningAction SilentlyContinue
}

function Wait-ListeningPort([string]$Name, [int]$Port) {
    $deadline = (Get-Date).AddSeconds(90)
    while ((Get-Date) -lt $deadline) {
        if (Test-ListeningPort $Port) { return }
        Start-Sleep -Milliseconds 500
    }
    throw "$Name did not open port $Port within 90 seconds. Check $LogRoot."
}

foreach ($target in $Targets) {
    $wrapper = Join-Path $target.Root 'gradlew.bat'
    if (-not (Test-Path -LiteralPath $wrapper)) {
        throw "Missing $($target.Name) Gradle wrapper: $wrapper"
    }
}

$clientWrapper = Join-Path $ClientRoot 'gradlew.bat'
if (-not (Test-Path -LiteralPath $clientWrapper)) {
    throw "Missing client Gradle wrapper: $clientWrapper"
}

if (-not $Launch) {
    Write-Host 'Preview only. No process was started.'
    foreach ($target in $Targets) {
        Write-Host "$($target.Name): $($target.Root) / $($target.Arguments) / port $($target.Port)"
    }
    if (-not $SkipClient) {
        Write-Host "client: $ClientRoot / :runescape:run --no-daemon"
    }
    Write-Host 'Run .\Launch-RSPS.ps1 -Launch to start in order.'
    exit 0
}

New-Item -ItemType Directory -Path $LogRoot -Force | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'

foreach ($target in $Targets) {
    if (Test-ListeningPort $target.Port) {
        Write-Host "$($target.Name) already listens on $($target.Port); leaving it untouched."
        continue
    }

    $out = Join-Path $LogRoot "$($target.Name -replace ' ', '-')-$stamp.out.log"
    $err = Join-Path $LogRoot "$($target.Name -replace ' ', '-')-$stamp.err.log"
    Start-Process -FilePath (Join-Path $target.Root 'gradlew.bat') -ArgumentList $target.Arguments -WorkingDirectory $target.Root -WindowStyle Hidden -RedirectStandardOutput $out -RedirectStandardError $err
    Wait-ListeningPort $target.Name $target.Port
    Write-Host "$($target.Name) is listening on $($target.Port)."
}

if (-not $SkipClient) {
    $out = Join-Path $LogRoot "client-$stamp.out.log"
    $err = Join-Path $LogRoot "client-$stamp.err.log"
    Start-Process -FilePath $clientWrapper -ArgumentList ':runescape:run --no-daemon' -WorkingDirectory $ClientRoot -WindowStyle Hidden -RedirectStandardOutput $out -RedirectStandardError $err
    Write-Host 'Client start requested.'
}
