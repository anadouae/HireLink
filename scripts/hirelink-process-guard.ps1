# HireLink — garde-fou processus (demo 8081/5500 intouchables).
# Ne jamais tuer java/maven globalement. N'autoriser l'arrêt que d'un PID
# explicitement marqué isolé et hors ports protégés.

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:ProtectedPorts = @(8081, 5500)
$script:ProtectedJarMarkers = @(
    'hirelink-demo-8081.jar',
    'docs\deploy-prep\demo-runtime\',
    'docs/deploy-prep/demo-runtime/'
)

function Get-ListenPid([int]$Port) {
    $lines = netstat -ano | Select-String -Pattern (":$Port\s+") | ForEach-Object { $_.Line }
    foreach ($line in $lines) {
        if ($line -match 'LISTENING\s+(\d+)\s*$') { return [int]$Matches[1] }
    }
    return $null
}

function Get-ProtectedPids {
    $pids = [System.Collections.Generic.HashSet[int]]::new()
    foreach ($port in $script:ProtectedPorts) {
        $p = Get-ListenPid $port
        if ($p) { [void]$pids.Add($p) }
    }
    Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue | ForEach-Object {
        $cmd = [string]$_.CommandLine
        foreach ($m in $script:ProtectedJarMarkers) {
            if ($cmd -and $cmd.IndexOf($m, [StringComparison]::OrdinalIgnoreCase) -ge 0) {
                [void]$pids.Add([int]$_.ProcessId)
            }
        }
    }
    return @($pids)
}

function Test-IsProtectedProcess([int]$ProcessId, [string]$CommandLine) {
    $protected = Get-ProtectedPids
    if ($protected -contains $ProcessId) { return $true }
    if ([string]::IsNullOrEmpty($CommandLine)) { return $false }
    foreach ($m in $script:ProtectedJarMarkers) {
        if ($CommandLine.IndexOf($m, [StringComparison]::OrdinalIgnoreCase) -ge 0) { return $true }
    }
    # Filet de sécurité : toute écoute 8081/5500 via ce PID
    foreach ($port in $script:ProtectedPorts) {
        $listen = Get-ListenPid $port
        if ($listen -eq $ProcessId) { return $true }
    }
    return $false
}

<#
.SYNOPSIS
  Arrête uniquement les processus dont la ligne de commande contient $Marker
  ET qui ne sont pas protégés (8081/5500 / JAR démo).
#>
function Stop-HireLinkMarkedProcesses {
    param(
        [Parameter(Mandatory = $true)][string]$Marker,
        [switch]$WhatIf
    )
    if ([string]::IsNullOrWhiteSpace($Marker)) { throw 'Marker requis' }
    if ($Marker -match '(?i)hirelink-demo-8081|demo-runtime|:\s*8081|:\s*5500') {
        throw "Marker interdit (trop large / protégé) : $Marker"
    }
    # Interdit les motifs trop larges qui ont causé l'incident B0
    if ($Marker -match '(?i)^(java|maven|mvn|surefire|hirelink)$') {
        throw "Marker trop large interdit : $Marker — utiliser un marqueur unique (ex. b0-fatjar-smoke, 18084)"
    }

    $stopped = @()
    Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object {
        $_.CommandLine -and ($_.CommandLine.IndexOf($Marker, [StringComparison]::OrdinalIgnoreCase) -ge 0)
    } | ForEach-Object {
        $pidToStop = [int]$_.ProcessId
        $cmd = [string]$_.CommandLine
        if (Test-IsProtectedProcess -ProcessId $pidToStop -CommandLine $cmd) {
            Write-Warning "REFUS arrêt PID $pidToStop (processus protégé démo)"
            return
        }
        if ($WhatIf) {
            Write-Output "WHATIF stop $pidToStop $($_.Name)"
            return
        }
        Stop-Process -Id $pidToStop -Force -ErrorAction SilentlyContinue
        $stopped += $pidToStop
        Write-Output "stopped $pidToStop"
    }
    return $stopped
}

function Assert-DemoStillUp {
    $h = Invoke-RestMethod 'http://127.0.0.1:8081/api/v1/health' -TimeoutSec 5
    if ($h.status -ne 'UP') { throw 'Démo 8081 health != UP' }
    Write-Output "demo_health=UP pid=$(Get-ListenPid 8081)"
}
