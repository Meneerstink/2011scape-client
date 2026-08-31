[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-ListeningProcessId([int]$Port) {
    $line = netstat -ano | Where-Object { $_ -match "^\s*TCP\s+.*:$Port\s+.*LISTENING\s+(\d+)\s*$" } | Select-Object -First 1
    if ($null -eq $line) { return $null }
    return [int]([regex]::Match($line, 'LISTENING\s+(\d+)\s*$').Groups[1].Value)
}

$gamePid = Get-ListeningProcessId 50015
if ($null -ne $gamePid) {
    Write-Host "Stopping current game server (PID $gamePid)..."
    & taskkill.exe /PID $gamePid | Out-Host
    $deadline = (Get-Date).AddSeconds(30)
    while ((Get-Date) -lt $deadline -and $null -ne (Get-ListeningProcessId 50015)) {
        Start-Sleep -Milliseconds 500
    }
    if ($null -ne (Get-ListeningProcessId 50015)) {
        throw 'Game server did not close gracefully within 30 seconds. It was not force-killed.'
    }
}

& (Join-Path $PSScriptRoot 'Launch-RSPS.ps1') -Launch
