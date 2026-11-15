# Smoke du fat JAR candidat B0 sur port 18084 / DB isolée.
# Arrêt UNIQUEMENT des process marqués b0-fatjar-smoke. Jamais 8081/5500.
# Usage : pwsh -File scripts/smoke-b0-fat-jar.ps1

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'hirelink-process-guard.ps1')

$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$Java = 'C:\Program Files\Java\jdk-23\bin\java.exe'
$Jar = Join-Path $Root 'docs\deploy-prep\b0-candidate-runtime\hirelink-b0-backend.jar'
$Props = Join-Path $Root 'build-iso\b0-fatjar\application-b0-fatjar-smoke.properties'
$Evidence = Join-Path $Root 'docs\evidence\b0-safe-cutover'
$Marker = 'b0-fatjar-smoke'
$Port = 18084
$DemoJar = Join-Path $Root 'docs\deploy-prep\demo-runtime\hirelink-demo-8081.jar'

New-Item -ItemType Directory -Force -Path $Evidence | Out-Null
if (-not (Test-Path $Jar)) { throw "JAR candidat manquant : $Jar — lancer build-b0-fat-jar.ps1 d'abord" }
if (-not (Test-Path $Java)) { throw "JDK 23 manquant" }

Assert-DemoStillUp
$demoHashBefore = (Get-FileHash $DemoJar -Algorithm SHA256).Hash
$candHash = (Get-FileHash $Jar -Algorithm SHA256).Hash

& 'C:\xampp\mysql\bin\mysql.exe' -u root -e "CREATE DATABASE IF NOT EXISTS hirelink_b0_fatjar_smoke CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# Nettoyage éventuel d'un smoke précédent (marqueur strict)
Stop-HireLinkMarkedProcesses -Marker $Marker | Out-Null
Start-Sleep -Seconds 1

$log = Join-Path $Evidence 'fatjar-18084.log'
$err = Join-Path $Evidence 'fatjar-18084.err.log'
Remove-Item $log, $err -Force -ErrorAction SilentlyContinue

# -Dhirelink.smoke.marker embarqué dans cmdline pour Stop-HireLinkMarkedProcesses
$args = @(
    "-Dhirelink.smoke.marker=$Marker",
    '-jar', $Jar,
    "--spring.config.additional-location=file:$Props",
    "--spring.application.name=$Marker"
)
$proc = Start-Process -FilePath $Java -ArgumentList $args -WorkingDirectory (Join-Path $Root 'backend') `
    -RedirectStandardOutput $log -RedirectStandardError $err -PassThru -WindowStyle Hidden
"started_iso_pid=$($proc.Id)"

$ok = $false
# DDL MySQL 5.x / premier boot : souvent 4–8 min — ne pas couper trop tôt.
for ($i = 0; $i -lt 150; $i++) {
    Start-Sleep -Seconds 2
    if ($proc.HasExited) { throw "Process iso exit=$($proc.ExitCode) — voir $err" }
    try {
        $h = Invoke-RestMethod "http://127.0.0.1:$Port/api/v1/health" -TimeoutSec 3
        if ($h.status -eq 'UP') { $ok = $true; "iso_health_up after=$($i*2)s"; break }
    } catch {}
}
if (-not $ok) { throw "Smoke health timeout — voir $log / $err" }

# Seed exclude-sources proof
$sql = @"
INSERT INTO external_job_offers (source, external_id, title, company, origin_url, url_quality, available, quarantined, hidden_by_admin, published_at, last_synced_at, last_seen_at, description, location, contract_label, skills)
VALUES
('MAROC_EMPLOI','fat-m1','Exclu Maroc','Fixture','https://jobs.lever.co/fixture/abcdef12-3456-7890-abcd-ef1234567890','DEEP',1,0,0,UTC_TIMESTAMP(),UTC_TIMESTAMP(),UTC_TIMESTAMP(),'d','Casablanca','CDI','Java'),
('REMOTE_OK','fat-r1','Autorise Remote','Acme','https://remoteok.com/remote-jobs/fat-r1','DEEP',1,0,0,UTC_TIMESTAMP(),UTC_TIMESTAMP(),UTC_TIMESTAMP(),'d','Casablanca','CDI','Java')
ON DUPLICATE KEY UPDATE title=VALUES(title), available=1, quarantined=0, hidden_by_admin=0;
"@
& 'C:\xampp\mysql\bin\mysql.exe' -u root hirelink_b0_fatjar_smoke -e $sql | Out-Null

$ext = Invoke-RestMethod "http://127.0.0.1:$Port/api/v1/external-offers" -TimeoutSec 10
$titles = @($ext | ForEach-Object { $_.title })
$sources = @($ext | ForEach-Object { $_.source })
$cat = Invoke-RestMethod "http://127.0.0.1:$Port/api/v1/catalog/offers?page=0&size=5&country=ALL" -TimeoutSec 10

$passExclude = ($titles -contains 'Autorise Remote') -and -not ($titles -contains 'Exclu Maroc') -and -not ($sources -contains 'MAROC_EMPLOI')
if (-not $passExclude) { throw "B0 exclude FAIL titles=$($titles -join ',')" }

Assert-DemoStillUp
$demoHashAfter = (Get-FileHash $DemoJar -Algorithm SHA256).Hash
if ($demoHashBefore -ne $demoHashAfter) { throw 'JAR démo modifié pendant smoke' }

@"
smoke_at=$(Get-Date -Format o)
iso_port=$Port
iso_pid=$($proc.Id)
iso_db=hirelink_b0_fatjar_smoke
candidate_sha256=$candHash
external_count=$($ext.Count)
titles=$($titles -join '|')
sources=$($sources -join '|')
exclude_pass=$passExclude
catalog_total=$($cat.totalElements)
demo_health=UP
demo_sha_unchanged=True
"@ | Set-Content (Join-Path $Evidence 'fatjar-smoke-result.txt') -Encoding utf8

# Arrêt strict par marqueur uniquement
$stopped = Stop-HireLinkMarkedProcesses -Marker $Marker
Start-Sleep -Seconds 2
try {
    Invoke-RestMethod "http://127.0.0.1:$Port/api/v1/health" -TimeoutSec 2 | Out-Null
    throw 'ISO encore UP après stop — abort'
} catch {
    if ($_.Exception.Message -match 'encore UP') { throw }
    "iso_down=OK"
}
Assert-DemoStillUp
"SMOKE_PASS stopped=$($stopped -join ',') demo_pid=$(Get-ListenPid 8081)"
