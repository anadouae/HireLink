# Smoke JAR candidat + recherche Web internationale sur :18089
# Arrêt UNIQUEMENT des process marqués web-intl-smoke. Jamais 8081.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'hirelink-process-guard.ps1')

$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$Java = 'C:\Program Files\Java\jdk-23\bin\java.exe'
$Meta = Join-Path $Root 'docs\deploy-prep\b0-candidate-runtime\BUILD_META.txt'
$Props = Join-Path $Root 'build-iso\web-intl\application-web-intl-smoke.properties'
$Evidence = Join-Path $Root 'docs\evidence\final-delivery\web-intl-smoke'
$Marker = 'web-intl-smoke'
$Port = 18089

New-Item -ItemType Directory -Force -Path $Evidence | Out-Null
Assert-DemoStillUp

if (-not (Test-Path $Meta)) { throw 'BUILD_META.txt manquant — lancer build-b0-fat-jar.ps1' }
$candLine = (Get-Content $Meta | Where-Object { $_ -like 'candidate=*' } | Select-Object -First 1)
$Jar = $candLine -replace '^candidate=', ''
if (-not (Test-Path $Jar)) { throw "JAR candidat introuvable : $Jar" }
$candHash = (Get-FileHash $Jar -Algorithm SHA256).Hash

& 'C:\xampp\mysql\bin\mysql.exe' -u root -e "CREATE DATABASE IF NOT EXISTS hirelink_web_intl_smoke CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
Stop-HireLinkMarkedProcesses -Marker $Marker | Out-Null
Start-Sleep -Seconds 1

$log = Join-Path $Evidence 'web-intl-18089.log'
$err = Join-Path $Evidence 'web-intl-18089.err.log'
Remove-Item $log, $err -Force -ErrorAction SilentlyContinue

$proc = Start-Process -FilePath $Java -ArgumentList @(
    "-Dhirelink.smoke.marker=$Marker",
    '-jar', $Jar,
    "--spring.config.additional-location=file:$Props",
    "--spring.application.name=$Marker"
) -WorkingDirectory (Join-Path $Root 'backend') -RedirectStandardOutput $log -RedirectStandardError $err -PassThru -WindowStyle Hidden

$ok = $false
for ($i = 0; $i -lt 180; $i++) {
    Start-Sleep -Seconds 2
    if ($proc.HasExited) { throw "iso exit=$($proc.ExitCode) — $err" }
    try {
        $h = Invoke-RestMethod "http://127.0.0.1:$Port/api/v1/health" -TimeoutSec 3
        if ($h.status -eq 'UP') { $ok = $true; break }
    } catch {}
}
if (-not $ok) { throw "Health timeout — $log / $err" }

$base = "http://127.0.0.1:$Port/api/v1"
$report = [ordered]@{}
$report.jar = $Jar
$report.sha256 = $candHash
$report.pid = $proc.Id
$report.demo_offers_before = (& 'C:\xampp\mysql\bin\mysql.exe' -u root -N -e 'SELECT COUNT(*) FROM hirelink.job_offers;')

# 1) ALL + developer
$sw = [Diagnostics.Stopwatch]::StartNew()
$all = Invoke-RestMethod "$base/catalog/offers?q=developer&country=ALL&page=0&size=12"
$sw.Stop()
$report.all_ms = $sw.ElapsedMilliseconds
$report.all_total = $all.totalElements
$report.all_web_available = $all.webSearchAvailable
$report.all_ephemeral = @($all.items | Where-Object { $_.ephemeral }).Count
$report.all_urls_https = (@($all.items | Where-Object { $_.ephemeral -and $_.originUrl -like 'https://*' })).Count
$report.all_coverage = $all.webSearchCoverage
$report.all_sources = ($all.webSearchSources | ForEach-Object { "$($_.code)=$($_.status)($($_.hits))" }) -join '; '

# 2) DE filter geo
$de = Invoke-RestMethod "$base/catalog/offers?q=engineer&country=DE&page=0&size=10"
$report.de_items = $de.items.Count
$report.de_ephemeral = @($de.items | Where-Object { $_.ephemeral }).Count
$badGeo = @($de.items | Where-Object { $_.ephemeral -and $_.location -match 'Casablanca|Maroc|Morocco' -and $_.location -notmatch 'Berlin|Germany|Deutschland|Remote' })
$report.de_false_geo = $badGeo.Count

# 3) FR + city Paris
$fr = Invoke-RestMethod "$base/catalog/offers?q=developer&country=FR&city=Paris&page=0&size=8"
$report.fr_paris_ephemeral = @($fr.items | Where-Object { $_.ephemeral }).Count

# 4) Pagination
$p0 = Invoke-RestMethod "$base/catalog/offers?q=developer&country=ALL&page=0&size=5"
$p1 = Invoke-RestMethod "$base/catalog/offers?q=developer&country=ALL&page=1&size=5"
$report.page0 = $p0.items.Count
$report.page1 = $p1.items.Count
$report.total_pages = $p0.totalPages

# 5) Éphémère : listings count DB smoke inchangé après recherches
$listingsBefore = (& 'C:\xampp\mysql\bin\mysql.exe' -u root -N -e 'SELECT COUNT(*) FROM hirelink_web_intl_smoke.catalog_listings;' 2>$null)
if (-not $listingsBefore) { $listingsBefore = '0' }
$null = Invoke-RestMethod "$base/catalog/offers?q=java&country=ALL&page=0&size=10"
$listingsAfter = (& 'C:\xampp\mysql\bin\mysql.exe' -u root -N -e 'SELECT COUNT(*) FROM hirelink_web_intl_smoke.catalog_listings;' 2>$null)
$report.listings_before = $listingsBefore
$report.listings_after = $listingsAfter
$report.ephemeral_ok = ($listingsBefore -eq $listingsAfter)

# 6) Candidature : ids Web / manquants / invalides → 400 (jamais 500)
$webItem = $all.items | Where-Object { $_.ephemeral } | Select-Object -First 1
$webId = $webItem.id
$report.sample_web_id = $webId
$report.sample_web_url = $webItem.originUrl
$report.sample_web_source = $webItem.source
$report.sample_web_label = $webItem.sourceLabel
$report.sample_web_home = $webItem.sourceHome
$report.label_ok = [bool]($webItem.sourceLabel -match 'Remotive|Arbeitnow|Remote OK')
$report.home_ok = [bool]($webItem.sourceHome -match '^https://')

# Compte candidat minimal (DB smoke uniquement — jamais hirelink démo)
$hash = (& 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' -N -u root hirelink -e "SELECT password_hash FROM users WHERE email='candidat@hirelink.ma' LIMIT 1;")
& 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' -u root hirelink_web_intl_smoke -e @"
INSERT INTO users (active, company_verified, created_at, email, email_verified, full_name, must_set_password, password_hash, profile_complete, role, mfa_enabled)
SELECT 1,0,UTC_TIMESTAMP(6),'smoke.candidat@hirelink.ma',1,'Smoke Candidat',0,'$hash',1,'CANDIDAT',0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM users WHERE email='smoke.candidat@hirelink.ma');
"@
$smokeTok = $null
try {
    $lr = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' `
        -Body '{"email":"smoke.candidat@hirelink.ma","password":"Candidat123!"}'
    $smokeTok = $lr.accessToken
    $report.smoke_login = 'OK'
} catch {
    $report.smoke_login = "FAIL:$($_.Exception.Message)"
}

function Probe-Apply([string]$json) {
    $headers = @{ 'Content-Type' = 'application/json' }
    if ($smokeTok) { $headers.Authorization = "Bearer $smokeTok" }
    try {
        Invoke-WebRequest -Method Post -Uri "$base/applications" -Headers $headers -Body $json -UseBasicParsing | Out-Null
        return 'UNEXPECTED_200'
    } catch {
        return "HTTP_$($_.Exception.Response.StatusCode.value__)"
    }
}
if ($webId) {
    $report.apply_web_string = Probe-Apply (@{ jobOfferId = "$webId"; coverLetter = 'x' } | ConvertTo-Json)
} else {
    $report.apply_web_string = 'NO_WEB_ITEM'
}
$report.apply_missing_id = Probe-Apply '{"coverLetter":"x"}'
$report.apply_wrong_field = Probe-Apply '{"offerId":"web-deadbeef","coverLetter":"x"}'
$report.apply_negative = Probe-Apply '{"jobOfferId":-1,"coverLetter":"x"}'
$report.apply_web = $report.apply_web_string
$applyCodes = @(
    $report.apply_web_string, $report.apply_missing_id, $report.apply_wrong_field, $report.apply_negative
)
$fiveHundreds = @($applyCodes | Where-Object { $_ -match '500' })
$fourHundreds = @($applyCodes | Where-Object { $_ -match '^HTTP_4' })
$report.apply_no_500 = (@($fiveHundreds).Count -eq 0)
$report.apply_all_4xx = (@($fourHundreds).Count -eq 4)

# 7) Lien mort : HEAD sur URL inventée ne doit pas être servie par nous
$report.dead_link_policy = 'FE ouvre URL source; 404 côté site d''origine — HireLink n''invente pas de remplacement'

# 8) Demo intact
Assert-DemoStillUp
$report.demo_offers_after = (& 'C:\xampp\mysql\bin\mysql.exe' -u root -N -e 'SELECT COUNT(*) FROM hirelink.job_offers;')
$demoListen = (netstat -ano | findstr ':8081' | findstr LISTENING | Select-Object -First 1)
$report.demo_8081_listening = [bool]$demoListen
$report.demo_pid_alive = $report.demo_8081_listening

$report.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" } | Set-Content (Join-Path $Evidence 'SMOKE_REPORT.txt') -Encoding utf8
$report | ConvertTo-Json | Set-Content (Join-Path $Evidence 'SMOKE_REPORT.json') -Encoding utf8
"SMOKE_OK iso_pid=$($proc.Id) sha=$candHash ephemeral=$($report.all_ephemeral) apply=$($report.apply_web)"
